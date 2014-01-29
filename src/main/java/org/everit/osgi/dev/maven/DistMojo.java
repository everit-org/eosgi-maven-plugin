package org.everit.osgi.dev.maven;

import java.io.File;
import java.io.FileInputStream;
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
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
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
     * Comma separated list of ports of currently running OSGi containers. Such ports are normally opened with
     * richConsole. In case this property is defined, dependency changes will be pushed via the defined ports.
     */
    @Parameter(property = "eosgi.servicePort")
    protected String servicePort;

    /**
     * Comma separated list of the id of the environments that should be processed. Default is * that means all
     * environments.
     */
    @Parameter(property = "eosgi.environmentId")
    protected String environmentId = "*";

    /**
     * If link than the generated files in the dist folder will be links instead of real copied files. Two possible
     * values: symbolicLink, file.
     * 
     */
    @Parameter(property = "eosgi.copyMode", defaultValue = EOsgiConstants.COPYMODE_FILE)
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
        List<ProcessedArtifact> bundleArtifacts;
        File globalDistFolderFile = new File(getDistFolder());
        try {
            bundleArtifacts = getProcessedArtifacts();
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
        }

        distributedEnvironments = new ArrayList<DistributedEnvironment>();
        for (EnvironmentConfiguration environment : getEnvironments()) {
            Artifact distPackageArtifact = resolveDistPackage(environment);
            File distPackageFile = distPackageArtifact.getFile();
            File distFolderFile = new File(globalDistFolderFile, environment.getId());

            ZipFile distPackageZipFile = null;
            try {

                distPackageZipFile = new ZipFile(distPackageFile);
                DistUtil.unpackZipFile(distPackageFile, distFolderFile, false);

                if (sourceDistPath != null) {
                    File sourceDistPathFile = new File(sourceDistPath);
                    if (sourceDistPathFile.exists() && sourceDistPathFile.isDirectory()) {
                        DistUtil.copyDirectory(sourceDistPathFile, distFolderFile);
                    }
                }
                List<ArtifactWithSettings> distributedBundleArtifacts =
                        convertBundleArtifactsToDistributed(environment, bundleArtifacts);

                DistributionPackage distributionPackage =
                        parseConfiguration(distFolderFile, distributedBundleArtifacts, environment);

                Artifacts artifacts = distributionPackage.getArtifacts();
                if (artifacts != null) {
                    resolveAndCopyArtifacts(artifacts.getArtifact(), distFolderFile);
                }
                parseParseables(distributionPackage, distFolderFile, distributedBundleArtifacts, environment);
                distributedEnvironments.add(new DistributedEnvironment(environment, distributionPackage,
                        distFolderFile, distributedBundleArtifacts));

            } catch (ZipException e) {
                throw new MojoExecutionException("Could not uncompress distribution package file: "
                        + distPackageFile.toString(), e);
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
            final List<ArtifactWithSettings> bundleArtifacts, final EnvironmentConfiguration environment)
            throws MojoExecutionException {
        File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

        VelocityContext context = new VelocityContext();
        context.put("bundleArtifacts", bundleArtifacts);
        context.put("environment", environment);
        try {
            DistUtil.replaceFileWithParsed(configFile, context, "UTF8");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not run velocity on configuration file: " + configFile.getName(), e);
        }
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(configFile);
            Unmarshaller unmarshaller = distConfigJAXBContext.createUnmarshaller();
            Object distributionPackage = unmarshaller.unmarshal(inputStream);
            if (distributionPackage instanceof JAXBElement) {
                distributionPackage = ((JAXBElement<DistributionPackage>) distributionPackage).getValue();
            }
            if (distributionPackage instanceof DistributionPackage) {
                return (DistributionPackage) distributionPackage;
            } else {
                throw new MojoExecutionException("The root element in the provided distribution configuration file "
                        + "is not the expected DistributionPackage element");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not read configuration file in distribution package: "
                    + configFile.getName(), e);
        } catch (JAXBException e) {
            throw new MojoExecutionException("Could not read configuration file in distribution package: "
                    + configFile.getName(), e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    getLog().error("Could not close zip stream: " + configFile.getName(), e);
                }
            }
        }
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

    protected void resolveAndCopyArtifacts(
            final List<org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact> artifacts, final File envDistFolderFile)
            throws MojoExecutionException {
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
        if (EOsgiConstants.COPYMODE_SYMBOLIC_LINK.equals(getCopyMode())) {
            DistUtil.createSymbolicLinks(fileCopyMap, pluginArtifactMap, getLog());
        } else {
            for (Entry<File, File> fileCopyEntry : fileCopyMap.entrySet()) {
                if (!fileCopyEntry.getValue().exists()) {
                    DistUtil.copyFile(fileCopyEntry.getKey(), fileCopyEntry.getValue(), getLog());
                } else {
                    getLog().debug("Skipping to copy " + fileCopyEntry.getValue() + " as it already exists");
                }
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

        ArtifactRepository artifactRepository =
                artifactRepositoryFactory.createArtifactRepository("everit.groups.public",
                        "http://repository.everit.biz/nexus/content/groups/public", new DefaultRepositoryLayout(),
                        new ArtifactRepositoryPolicy(), new ArtifactRepositoryPolicy());

        List<ArtifactRepository> tmpRemoteRepositories = new ArrayList<ArtifactRepository>(remoteRepositories);
        tmpRemoteRepositories.add(artifactRepository);
        try {
            artifactResolver.resolve(distPackageArtifact, tmpRemoteRepositories, localRepository);
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
