/**
 * This file is part of Everit - Maven OSGi plugin.
 *
 * Everit - Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
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
import org.apache.maven.plugin.AbstractMojo;
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
import org.everit.osgi.dev.maven.util.FileManager;

/**
 * Creates a distribution package for the project. Distribution packages may be provided as Environment parameters or
 * 'equinox', the default option, -may also be used. The structure of the distribution package may be different for
 * different types.
 */
@Mojo(name = "dist", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class DistMojo extends AbstractMojo {

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

    /**
     * Comma separated list of the id of the environments that should be processed. Default is * that means all
     * environments.
     */
    @Parameter(property = "eosgi.environmentIds", defaultValue = "*")
    protected String environmentIds = "*";

    /**
     * The environments on which the tests should run.
     */
    @Parameter
    protected EnvironmentConfiguration[] environments;

    private EnvironmentConfiguration[] environmentsToProcess;

    @Parameter(defaultValue = "${executedProject}")
    protected MavenProject executedProject;

    private FileManager fileManager = null;

    @Parameter(defaultValue = "${localRepository}")
    protected ArtifactRepository localRepository;

    /**
     * Map of plugin artifacts.
     */
    @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
    protected Map<String, Artifact> pluginArtifactMap;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

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

    protected void addDefaultSettingsToEnvironment(final EnvironmentConfiguration environment)
            throws MojoExecutionException {
        String environmentId = environment.getId();
        Map<String, String> systemProperties = environment.getSystemProperties();
        String currentValue = systemProperties.get(EOsgiConstants.SYSPROP_ENVIRONMENT_ID);
        if (currentValue != null && !currentValue.equals(environmentId)) {
            throw new MojoExecutionException("If defined, the system property " + EOsgiConstants.SYSPROP_ENVIRONMENT_ID
                    + " must be the same as environment id: " + environment.getId());
        }
        if (currentValue == null) {
            systemProperties.put(EOsgiConstants.SYSPROP_ENVIRONMENT_ID, environmentId);
        }
    }

    protected void distributeArtifacts(final DistributionPackage distributionPackage, final File envDistFolderFile)
            throws MojoExecutionException {

        Artifacts artifactsJaxbObj = distributionPackage.getArtifacts();
        if (artifactsJaxbObj == null) {
            return;
        }
        List<org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact> artifacts = artifactsJaxbObj
                .getArtifact();

        CopyMode environmentCopyMode = distributionPackage.getCopyMode();
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
            File targetFileFolder = envDistFolderFile;
            if (artifact.getTargetFolder() != null) {
                targetFileFolder = new File(envDistFolderFile, artifact.getTargetFolder());
            }
            targetFileFolder.mkdirs();
            String targetFileName = artifact.getTargetFile();
            if (targetFileName == null) {
                targetFileName = mavenArtifact.getFile().getName();
                artifact.setTargetFile(targetFileName);
            }
            File targetFile = new File(targetFileFolder, targetFileName);

            CopyMode artifactCopyMode = environmentCopyMode;
            if (artifact.getCopyMode() != null) {
                artifactCopyMode = artifact.getCopyMode();
            }
            fileManager.copyFile(mavenArtifact.getFile(), targetFile, artifactCopyMode);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        fileManager = new FileManager(getLog());
        try {
            List<DistributableArtifact> processedArtifacts;
            File globalDistFolderFile = new File(getDistFolder());

            distributedEnvironments = new ArrayList<DistributedEnvironment>();
            for (EnvironmentConfiguration environment : getEnvironmentsToProcess()) {
                try {
                    processedArtifacts = generateDistributableArtifacts(environment);
                } catch (MalformedURLException e) {
                    throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
                }

                addDefaultSettingsToEnvironment(environment);
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
                if (CopyMode.SYMBOLIC_LINK.equals(environmentCopyMode) && !fileManager.isSystemSymbolicLinkCapable()) {
                    throw new MojoExecutionException(
                            "It seems that the operating system does not support symbolic links");
                }

                ZipFile distPackageZipFile = null;
                try {
                    distPackageZipFile = new ZipFile(distPackageFile);
                    fileManager.unpackZipFile(distPackageFile, distFolderFile);

                    if (sourceDistPath != null) {
                        File sourceDistPathFile = new File(sourceDistPath);
                        if (sourceDistPathFile.exists() && sourceDistPathFile.isDirectory()) {
                            fileManager.copyDirectory(sourceDistPathFile, distFolderFile, environmentCopyMode);
                        }
                    }

                    DistributionPackage distributionPackage =
                            parseConfiguration(distFolderFile, processedArtifacts, environment,
                                    environmentCopyMode);

                    distributeArtifacts(distributionPackage, distFolderFile);

                    parseParseables(distributionPackage, distFolderFile, processedArtifacts, environment);
                    distributedEnvironments.add(new DistributedEnvironment(environment, distributionPackage,
                            distFolderFile, processedArtifacts));

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
        } finally {
            try {
                fileManager.close();
            } catch (IOException e) {
                getLog().error("Could not close file manager", e);
            }
        }
    }

    public String getCopyMode() {
        return copyMode;
    }

    protected EnvironmentConfiguration getDefaultEnvironment() {
        getLog().info("There is no environment specified in the project. Creating equinox environment with"
                + " default settings");
        EnvironmentConfiguration defaultEnvironment = new EnvironmentConfiguration();
        defaultEnvironment.setId("equinox");
        defaultEnvironment.setFramework("equinox");
        return defaultEnvironment;
    }

    public String getDistFolder() {
        return distFolder;
    }

    public List<DistributedEnvironment> getDistributedEnvironments() {
        return distributedEnvironments;
    }

    public EnvironmentConfiguration[] getEnvironments() {
        if (environments == null || environments.length == 0) {
            environments = new EnvironmentConfiguration[] { getDefaultEnvironment() };
        }
        return environments;
    }

    /**
     * Getting an array of the environment configurations that should be processed based on the value of the
     * {@link #environmentIds} parameter. The value, that is returned, is calculated the first time the function is
     * called.
     * 
     * @return The array of environment ids that should be processed.
     */
    protected EnvironmentConfiguration[] getEnvironmentsToProcess() {
        if (environmentsToProcess != null) {
            return environmentsToProcess;
        }

        if ("*".equals(environmentIds)) {
            environmentsToProcess = getEnvironments();
        } else {
            String[] environmentIdArray = environmentIds.trim().split(",");

            EnvironmentConfiguration[] tmpEnvironments = getEnvironments();

            List<EnvironmentConfiguration> result = new ArrayList<EnvironmentConfiguration>();
            for (int i = 0; i < tmpEnvironments.length; i++) {
                boolean found = false;
                int j = 0, n = environmentIdArray.length;
                while (!found && j < n) {
                    if (environmentIdArray[j].equals(tmpEnvironments[j].getId())) {
                        found = true;
                        result.add(tmpEnvironments[i]);
                    }
                    j++;
                }
            }
            environmentsToProcess = result.toArray(new EnvironmentConfiguration[result.size()]);
        }
        return environmentsToProcess;
    }

    /**
     * Getting the processed artifacts of the project. The artifact list is calculated each time when the function is
     * called therefore the developer should not call it inside an iteration.
     * 
     * @return The list of dependencies that are OSGI bundles but do not have the scope "provided"
     * @throws MalformedURLException
     *             if the URL for the artifact is broken.
     */
    protected List<DistributableArtifact> generateDistributableArtifacts(EnvironmentConfiguration environment)
            throws MalformedURLException {
        @SuppressWarnings("unchecked")
        List<Artifact> availableArtifacts = new ArrayList<Artifact>(project.getArtifacts());
        availableArtifacts.add(project.getArtifact());

        List<DistributableArtifact> result = new ArrayList<DistributableArtifact>();
        for (Artifact artifact : availableArtifacts) {
            if (!Artifact.SCOPE_PROVIDED.equals(artifact.getScope())) {
                DistributableArtifact processedArtifact = DistUtil.processArtifact(environment, artifact);
                result.add(processedArtifact);
            }
        }
        return result;
    }

    protected DistributionPackage parseConfiguration(final File distFolderFile,
            final List<DistributableArtifact> distributableArtifacts, final EnvironmentConfiguration environment,
            final CopyMode environmentCopyMode)
            throws MojoExecutionException {
        File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

        VelocityContext context = new VelocityContext();
        context.put("artifacts", distributableArtifacts);
        context.put("environment", environment);
        context.put("copyMode", environmentCopyMode.value());
        try {
            fileManager.replaceFileWithParsed(configFile, context, "UTF8");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not run velocity on configuration file: " + configFile.getName(), e);
        }
        return readDistConfig(distFolderFile);
    }

    protected void parseParseables(final DistributionPackage distributionPackage, final File distFolderFile,
            final List<DistributableArtifact> distributableArtifacts, final EnvironmentConfiguration environment)
            throws MojoExecutionException {
        VelocityContext context = new VelocityContext();
        context.put("artifacts", distributableArtifacts);
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
                    fileManager.replaceFileWithParsed(parseableFile, context, p.getEncoding());
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
