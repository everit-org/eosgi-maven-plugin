/**
 * This file is part of Everit Maven OSGi plugin.
 *
 * Everit Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.zip.ZipFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.VelocityContext;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Artifacts;
import org.everit.osgi.dev.maven.jaxb.dist.definition.CopyMode;
import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackage;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ObjectFactory;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Parseable;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Parseables;
import org.everit.osgi.dev.maven.util.DistUtil;
import org.everit.osgi.dev.maven.util.EOsgiConstants;

/**
 * Creates a distribution package for the project. Distribution packages may be provided as Environment parameters or
 * 'equinox', the default option, -may also be used. The structure of the distribution package may be different for
 * different types.
 */
@Mojo(name = "dist", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class DistMojo extends AbstractOSGiMojo {

    @Component
    protected ArtifactFactory artifactFactory;

    @Component
    protected ArtifactRepositoryFactory artifactRepositoryFactory;

    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * If link than the generated files in the dist folder will be links instead of real copied files. Two possible
     * values: symbolicLink, file. In case this is an incrementel update, the default mode is the same as the mode of
     * the previous build. In case this is a clean build, the default mode is 'file'.
     */
    @Parameter(property = "eosgi.copyMode")
    protected String copyMode;

    protected final JAXBContext distConfigJAXBContext;

    /**
     * Path to folder where the distribution will be generated. The content of this folder will be overridden if the
     * files with same name already exist.
     * 
     */
    @Parameter(property = "eosgi.distFolder", defaultValue = "${project.build.directory}/eosgi-dist")
    protected String distFolder;

    protected List<DistributedEnvironment> distributedEnvironments;

    @Parameter(defaultValue = "${executedProject}")
    protected MavenProject executedProject;

    @Parameter(defaultValue = "${localRepository}")
    protected ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}")
    protected List<ArtifactRepository> remoteRepositories;

    /**
     * The directory where there may be additional files to create the distribution package.
     * 
     */
    @Parameter(property = "eosgi.sourceDistPath", defaultValue = "${basedir}/src/dist/")
    protected String sourceDistPath;

    /**
     * Comma separated list of ports of currently running OSGi containers. Such ports are normally opened with
     * richConsole. In case this property is defined, dependency changes will be pushed via the defined ports.
     */
    @Parameter(property = "eosgi.upgradePorts")
    protected String upgradePorts;

    public DistMojo() {
        try {
            distConfigJAXBContext =
                    JAXBContext.newInstance(ObjectFactory.class.getPackage().getName(),
                            ObjectFactory.class.getClassLoader());
        } catch (JAXBException e) {
            throw new RuntimeException("Could not create JAXB Context for distribution configuration file", e);
        }
    }

    protected List<ArtifactWithSettings> convertBundleArtifactsToDistributed(
            final EnvironmentConfiguration environment, final List<ProcessedArtifact> artifacts) {

        List<ArtifactWithSettings> distributedBundleArtifacts = new ArrayList<ArtifactWithSettings>();
        for (ProcessedArtifact artifact : artifacts) {
            distributedBundleArtifacts.add(generateDistributedArtifact(environment, artifact));
        }
        return distributedBundleArtifacts;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<ProcessedArtifact> processedArtifacts;
        File globalDistFolderFile = new File(getDistFolder());
        try {
            processedArtifacts = getProcessedArtifacts();
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
        }

        distributedEnvironments = new ArrayList<DistributedEnvironment>();
        for (EnvironmentConfiguration environment : getEnvironmentsToProcess()) {
            Artifact distPackageArtifact = resolveDistPackage(environment);
            File distPackageFile = distPackageArtifact.getFile();
            File distFolderFile = new File(globalDistFolderFile, environment.getId());

            CopyMode environmentCopyMode = (getCopyMode() != null) ? CopyMode.fromValue(getCopyMode()) : null;
            DistributionPackage existingDistConfig = readDistConfig(distFolderFile);
            if (existingDistConfig != null) {
                environmentCopyMode = existingDistConfig.getCopyMode();
            }
            if (environmentCopyMode == null) {
                environmentCopyMode = CopyMode.FILE;
            }

            ZipFile distPackageZipFile = null;
            try {
                distPackageZipFile = new ZipFile(distPackageFile);
                DistUtil.unpackZipFile(distPackageFile, distFolderFile);

                if (sourceDistPath != null) {
                    File sourceDistPathFile = new File(sourceDistPath);
                    if (sourceDistPathFile.exists() && sourceDistPathFile.isDirectory()) {
                        DistUtil.copyDirectory(sourceDistPathFile, distFolderFile, environmentCopyMode);
                    }
                }
                List<ArtifactWithSettings> distributedBundleArtifacts =
                        convertBundleArtifactsToDistributed(environment, processedArtifacts);

                DistributionPackage distributionPackage =
                        parseConfiguration(distFolderFile, distributedBundleArtifacts, environment,
                                environmentCopyMode);

                resolveAndCopyArtifacts(distributionPackage, distFolderFile);

                parseParseables(distributionPackage, distFolderFile, distributedBundleArtifacts, environment);
                distributedEnvironments.add(new DistributedEnvironment(environment, distributionPackage,
                        distFolderFile, distributedBundleArtifacts));

            } catch (IOException e) {
                throw new MojoExecutionException("Could not uncompress distribution package file: "
                        + distPackageFile.toString(), e);
            } finally {
                if (distPackageZipFile != null) {
                    try {
                        distPackageZipFile.close();
                    } catch (IOException e) {
                        getLog().error("Could not close distribution package zip file: " + distPackageZipFile, e);
                    }
                }
            }
        }
    }

    protected ArtifactWithSettings generateDistributedArtifact(final EnvironmentConfiguration environment,
            final ProcessedArtifact artifact) {

        getLog().debug("Converting artifact to distributable bundle artifact: " + artifact.toString());

        ArtifactWithSettings distributableBundleArtifact = new ArtifactWithSettings();
        distributableBundleArtifact.setBundleArtifact(artifact);

        // Getting the start level
        List<BundleSettings> bundleSettingsList = environment.getBundleSettings();
        Iterator<BundleSettings> iterator = bundleSettingsList.iterator();
        BundleSettings matchedSettings = null;
        while (iterator.hasNext() && (matchedSettings == null)) {
            BundleSettings settings = iterator.next();
            if (settings.getSymbolicName().equals(artifact.getSymbolicName())
                    && ((settings.getVersion() == null) || settings.getVersion().equals(artifact.getVersion()))) {
                matchedSettings = settings;
            }
        }
        if (matchedSettings != null) {

            distributableBundleArtifact.setStartLevel(matchedSettings.getStartLevel());
        }
        return distributableBundleArtifact;
    }

    public String getCopyMode() {
        return copyMode;
    }

    public String getDistFolder() {
        return distFolder;
    }

    public List<DistributedEnvironment> getDistributedEnvironments() {
        return distributedEnvironments;
    }

    protected DistributionPackage parseConfiguration(final File distFolderFile,
            final List<ArtifactWithSettings> bundleArtifacts, final EnvironmentConfiguration environment,
            final CopyMode copyMode)
            throws MojoExecutionException {
        File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

        VelocityContext context = new VelocityContext();
        context.put("bundleArtifacts", bundleArtifacts);
        context.put("environment", environment);
        context.put("copyMode", copyMode.toString());
        try {
            DistUtil.replaceFileWithParsed(configFile, context, "UTF8");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not run velocity on configuration file: " + configFile.getName(), e);
        }
        return readDistConfig(distFolderFile);
    }

    protected void parseParseables(final DistributionPackage distributionPackage, final File distFolderFile,
            final List<ArtifactWithSettings> bundleArtifacts, final EnvironmentConfiguration environment)
            throws MojoExecutionException {
        VelocityContext context = new VelocityContext();
        context.put("bundleArtifacts", bundleArtifacts);
        context.put("distributionPackage", distributionPackage);
        context.put("environment", environment);
        Parseables parseables = distributionPackage.getParseables();
        if (parseables != null) {
            List<Parseable> parseable = parseables.getParseable();
            for (Parseable p : parseable) {
                String path = p.getPath();
                File parseableFile = new File(distFolderFile, path);
                if (!parseableFile.exists()) {
                    throw new MojoExecutionException("File that should be parsed does not exist: "
                            + parseableFile.getAbsolutePath());
                }
                try {
                    DistUtil.replaceFileWithParsed(parseableFile, context, p.getEncoding());
                } catch (IOException e) {
                    throw new MojoExecutionException("Could not replace parseable with parsed content: " + p.getPath(),
                            e);
                }
            }
        }
    }

    /**
     * Reading up the content of each /META-INF/eosgi-frameworks.properties file from the classpath of the plugin.
     * 
     * @return The merged properties file.
     * @throws IOException
     *             if a read error occurs.
     */
    protected Properties readDefaultFrameworkPops() throws IOException {
        Enumeration<URL> resources =
                this.getClass().getClassLoader().getResources("META-INF/eosgi-frameworks.properties");
        Properties result = new Properties();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            Properties tmpProps = new Properties();
            InputStream inputStream = resource.openStream();
            try {
                tmpProps.load(inputStream);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            result.putAll(tmpProps);
        }
        return result;
    }

    protected DistributionPackage readDistConfig(final File distFolderFile) throws MojoExecutionException {
        File distConfigFile = new File(distFolderFile, "/.eosgi.dist.xml");
        if (distConfigFile.exists()) {
            try {
                Unmarshaller unmarshaller = distConfigJAXBContext.createUnmarshaller();
                Object distributionPackage = unmarshaller.unmarshal(distConfigFile);
                if (distributionPackage instanceof JAXBElement) {

                    @SuppressWarnings("unchecked")
                    JAXBElement<DistributionPackage> jaxbDistPack = (JAXBElement<DistributionPackage>) distributionPackage;
                    distributionPackage = jaxbDistPack.getValue();
                }
                if (distributionPackage instanceof DistributionPackage) {
                    return (DistributionPackage) distributionPackage;
                } else {
                    throw new MojoExecutionException(
                            "The root element in the provided distribution configuration file "
                                    + "is not the expected DistributionPackage element");
                }
            } catch (JAXBException e) {
                throw new MojoExecutionException("Failed to process already existing distribution configuration file: "
                        + distConfigFile.getAbsolutePath());
            }
        } else {
            return null;
        }
    }

    protected void resolveAndCopyArtifacts(final DistributionPackage distributionPackage, final File envDistFolderFile)
            throws MojoExecutionException {

        Artifacts artifactsJaxbObj = distributionPackage.getArtifacts();
        if (artifactsJaxbObj == null) {
            return;
        }
        List<org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact> artifacts = artifactsJaxbObj
                .getArtifact();

        CopyMode environmentCopyMode = distributionPackage.getCopyMode();
        Map<File, File> fileCopyMap = new HashMap<File, File>();
        for (org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact artifact : artifacts) {

            String artifactType = artifact.getType();
            if (artifactType == null) {
                artifactType = "jar";
            }
            Artifact mavenArtifact = null;
            if (artifact.getClassifier() == null) {
                mavenArtifact =
                        artifactFactory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                                artifact.getVersion(), "compile", artifactType);
            } else {
                mavenArtifact =
                        artifactFactory.createArtifactWithClassifier(artifact.getGroupId(), artifact.getArtifactId(),
                                artifact.getVersion(), artifactType, artifact.getClassifier());

            }
            try {
                artifactResolver.resolve(mavenArtifact, remoteRepositories, localRepository);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException("Could not resolve artifact for creating distribution package", e);
            } catch (ArtifactNotFoundException e) {
                throw new MojoExecutionException("Could not resolve artifact for creating distribution package", e);
            }
            File targetFileFolder = new File(envDistFolderFile, artifact.getTargetFolder());
            targetFileFolder.mkdirs();
            String targetFileName = artifact.getTargetFile();
            if (targetFileName == null) {
                targetFileName = mavenArtifact.getFile().getName();
                artifact.setTargetFile(targetFileName);
            }
            File targetFile = new File(targetFileFolder, targetFileName);
            fileCopyMap.put(mavenArtifact.getFile(), targetFile);
        }

        if (EOsgiConstants.COPYMODE_SYMBOLIC_LINK.equals(environmentCopyMode)) {
            for (Entry<File, File> fileCopyEntry : fileCopyMap.entrySet()) {
                DistUtil.copyFile(fileCopyEntry.getKey(), fileCopyEntry.getValue(), environmentCopyMode);
            }
        }
    }

    protected Artifact resolveDistPackage(final EnvironmentConfiguration environment) throws MojoExecutionException {
        String[] distPackageIdParts;
        try {
            distPackageIdParts = resolveDistPackageId(environment);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not get distribution package", e);
        }
        Artifact distPackageArtifact =
                artifactFactory.createArtifact(distPackageIdParts[0], distPackageIdParts[1], distPackageIdParts[2],
                        "compile", "zip");

        try {
            artifactResolver.resolve(distPackageArtifact, remoteRepositories, localRepository);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Could not resolve distribution artifact: "
                    + distPackageArtifact.getArtifactId(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Could not resolve distribution artifact: "
                    + distPackageArtifact.getArtifactId(), e);
        }
        return distPackageArtifact;
    }

    /**
     * 
     * @return A three length string array that contains the groupId, artifactId and version of the dist package.
     * @throws IOException
     *             if the resrources of the default framework id configurations cannot be read.
     * @throws MojoExecutionException
     *             if the distPackage expression configured for this plugin has wrong format.
     */
    protected String[] resolveDistPackageId(final EnvironmentConfiguration environment) throws IOException,
            MojoExecutionException {
        String frameworkArtifact = environment.getFramework();
        String[] distPackageParts = frameworkArtifact.split("\\:");
        if (distPackageParts.length == 1) {
            Properties defaultFrameworkPops = readDefaultFrameworkPops();
            String defaultFrameworkDistPackage = defaultFrameworkPops.getProperty(frameworkArtifact);
            if (defaultFrameworkDistPackage == null) {
                getLog().error(
                        "Could not find entry in any of the /META-INF/eosgi-frameworks.properites configuration "
                                + "files on the classpath for the framework id " + frameworkArtifact);
                throw new MojoExecutionException("Could not find framework dist package [" + frameworkArtifact + "]");
            } else {
                distPackageParts = defaultFrameworkDistPackage.split("\\:");
                getLog().info(
                        "Dist package definition '" + frameworkArtifact + "'  was resolved to be '"
                                + defaultFrameworkDistPackage + "'");
            }
        }
        if (distPackageParts.length != 3) {
            throw new MojoExecutionException("Invalid distribution package id format: " + frameworkArtifact);
        }
        return distPackageParts;
    }

}
