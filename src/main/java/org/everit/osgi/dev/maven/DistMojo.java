package org.everit.osgi.dev.maven;

/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
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
import org.apache.maven.project.MavenProject;
import org.apache.velocity.VelocityContext;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Artifacts;
import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackage;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ObjectFactory;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Parseable;
import org.everit.osgi.dev.maven.jaxb.dist.definition.Parseables;
import org.everit.osgi.dev.maven.util.DistUtil;

/**
 * Creates a distribution package for the project. A distribution packages may be provided as Environment parameters or
 * the default 'equinox' is used. The structure of the distribution package may be different for different types.
 * 
 * @goal dist
 * @phase package
 * @requiresProject true
 * @requiresDependencyResolution compile
 * @execute phase="package"
 */
public class DistMojo extends AbstractOSGIMojo {

    /** @component */
    protected ArtifactFactory artifactFactory;

    /** @component */
    protected ArtifactRepositoryFactory artifactRepositoryFactory;

    /**
     * @component
     */
    protected ArtifactResolver artifactResolver;

    /** @parameter default-value="${project.remoteArtifactRepositories}" */
    protected List<ArtifactRepository> remoteRepositories;

    /** @parameter default-value="${localRepository}" */
    protected ArtifactRepository localRepository;

    /**
     * @parameter expression="${executedProject}"
     */
    protected MavenProject executedProject;

    /**
     * Whether to include the test runner and it's dependencies.
     * 
     * @parameter expression="${eosgi.includeTestRunner}" default-value="false"
     */
    protected boolean includeTestRunner = false;

    /**
     * Whether to include the artifact of the current project or not. If false only the dependencies will be processed.
     * 
     * @parameter expression="${eosgi.includeCurrentProject}" default-value="false"
     */
    protected boolean includeCurrentProject = false;

    /**
     * Path to folder where the distribution will be generated. The content of this folder will be overridden if the
     * files with same name already exist.
     * 
     * @parameter expression="${eosgi.distFolder}" default-value="${project.build.directory}/eosgi-dist"
     */
    protected String distFolder;

    /**
     * If link than the generated files in the dist folder will be links instead of real copied files. Two possible
     * values: link, file.
     * 
     * @parameter expression="${eosgi.copyMode}" default-value="file"
     */
    protected String copyMode;

    protected List<DistributedEnvironment> distributedEnvironments;

    protected final JAXBContext distConfigJAXBContext;
    /**
     * The path of the zip file in which the distribution package will be generated. If the zip file already exists it
     * will be overridden. In zip distribution only copyMode file works.
     * 
     * @parameter expression="${eosgi.distZipUrl}"
     *            default-value="${project.build.directory}/${project.artifactId}-dist.zip"
     */
    protected String distZipPath;

    /**
     * The directory where there may be additional files to create the distribution package.
     * 
     * @parameter expression="${eosgi.sourceDistPath}" default-value="${basedir}/src/dist/"
     */
    protected String sourceDistPath;

    public DistMojo() {
        super();
        try {
            distConfigJAXBContext = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName(),
                    ObjectFactory.class.getClassLoader());
        } catch (JAXBException e) {
            throw new RuntimeException("Could not create JAXB Context for distribution configuration file", e);
        }
    }
    
    public List<DistributedEnvironment> getDistributedEnvironments() {
        return distributedEnvironments;
    }

    public boolean isIncludeCurrentProject() {
        return includeCurrentProject;
    }

    public boolean isIncludeTestRunner() {
        return includeTestRunner;
    }

    public String getCopyMode() {
        return copyMode;
    }
    
    public String getDistFolder() {
        return distFolder;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<BundleArtifact> bundleArtifacts;
        File globalDistFolderFile = new File(getDistFolder());
        try {
            bundleArtifacts = getBundleArtifacts(isIncludeCurrentProject(), isIncludeTestRunner());
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
        }
        Environment[] distEnvironments = environments;
        if (distEnvironments == null || distEnvironments.length == 0) {
            distEnvironments = new Environment[] { getDefaultEnvironment() };
        }
        environments = distEnvironments;
        distributedEnvironments = new ArrayList<DistributedEnvironment>();
        for (Environment distEnvironment : distEnvironments) {
            Artifact distPackageArtifact = resolveDistPackage(distEnvironment);
            File distPackageFile = distPackageArtifact.getFile();
            File distFolderFile = new File(globalDistFolderFile, distEnvironment.getId());

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

                DistributionPackage distributionPackage = parseConfiguration(distFolderFile, bundleArtifacts,
                        distEnvironment);
                Artifacts artifacts = distributionPackage.getArtifacts();
                if (artifacts != null) {
                    resolveAndCopyArtifacts(artifacts.getArtifact(), distFolderFile);
                }
                parseParseables(distributionPackage, distFolderFile, bundleArtifacts, distEnvironment);
                distributedEnvironments.add(new DistributedEnvironment(distEnvironment, distributionPackage,
                        distFolderFile, bundleArtifacts));

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
                        getLog().error("Could not close distribution package zip file: "
                                + distPackageZipFile, e);
                    }
                }
            }
        }
    }

    protected Environment getDefaultEnvironment() {
        Environment defaultEnvironment = new Environment();
        defaultEnvironment.setId("equinox");
        defaultEnvironment.setFramework("equinox");
        return defaultEnvironment;
    }

    protected void parseParseables(DistributionPackage distributionPackage, File distFolderFile,
            List<BundleArtifact> bundleArtifacts, Environment environment) throws MojoExecutionException {
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

    protected void resolveAndCopyArtifacts(List<org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact> artifacts,
            File envDistFolderFile)
            throws MojoExecutionException {
        Map<File, File> fileCopyMap = new HashMap<File, File>();
        for (org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact artifact : artifacts) {

            String artifactType = artifact.getType();
            if (artifactType == null) {
                artifactType = "jar";
            }
            Artifact mavenArtifact = null;
            if (artifact.getClassifier() == null) {
                mavenArtifact = artifactFactory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                        artifact.getVersion(),
                        "compile", artifactType);
            } else {
                mavenArtifact = artifactFactory.createArtifactWithClassifier(artifact.getGroupId(),
                        artifact.getArtifactId(),
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
        if ("link".equals(getCopyMode())) {
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

    protected DistributionPackage parseConfiguration(File distFolderFile, List<BundleArtifact> bundleArtifacts,
            Environment environment)
            throws MojoExecutionException {
        File configFile = new File(distFolderFile, "/.eosgi.dist.xml");

        VelocityContext context = new VelocityContext();
        context.put("bundleArtifacts", bundleArtifacts);
        context.put("environment", environment);
        try {
            DistUtil.replaceFileWithParsed(configFile, context, "UTF8");
        } catch (IOException e) {
            throw new MojoExecutionException("Could not run velocity on configuration file: " + configFile.getName(),
                    e);
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

    protected Artifact resolveDistPackage(Environment environment) throws MojoExecutionException {
        String[] distPackageIdParts;
        try {
            distPackageIdParts = resolveDistPackageId(environment);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not get distribution package", e);
        }
        Artifact distPackageArtifact = artifactFactory.createArtifact(distPackageIdParts[0], distPackageIdParts[1],
                distPackageIdParts[2], "compile", "zip");

        ArtifactRepository artifactRepository = artifactRepositoryFactory.createArtifactRepository(
                "everit.groups.public",
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
    protected String[] resolveDistPackageId(Environment environment) throws IOException, MojoExecutionException {
        String frameworkArtifact = environment.getFramework();
        String[] distPackageParts = frameworkArtifact.split("\\:");
        if (distPackageParts.length == 1) {
            Properties defaultFrameworkPops = readDefaultFrameworkPops();
            String defaultFrameworkDistPackage = defaultFrameworkPops.getProperty(frameworkArtifact);
            if (defaultFrameworkDistPackage == null) {
                getLog().error(
                        "Could not find entry in any of the /META-INF/eosgi-frameworks.properites configuration "
                                + "files on the classpath for the framework id "
                                + frameworkArtifact);
                throw new MojoExecutionException("Could not find framework dist package [" + frameworkArtifact
                        + "]");
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

    /**
     * Reading up the content of each /META-INF/eosgi-frameworks.properties file from the classpath of the plugin.
     * 
     * @return The merged properties file.
     * @throws IOException
     *             if a read error occurs.
     */
    protected Properties readDefaultFrameworkPops() throws IOException {
        Enumeration<URL> resources = this.getClass().getClassLoader()
                .getResources("META-INF/eosgi-frameworks.properties");
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

}
