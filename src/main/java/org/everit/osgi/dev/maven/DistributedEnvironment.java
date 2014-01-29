package org.everit.osgi.dev.maven;

import java.io.File;
import java.util.List;

import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackage;

public class DistributedEnvironment {

    private List<ArtifactWithSettings> bundleArtifacts;

    private File distributionFolder;

    private DistributionPackage distributionPackage;

    private EnvironmentConfiguration environment;

    public DistributedEnvironment() {
    }

    public DistributedEnvironment(final EnvironmentConfiguration environment,
            final DistributionPackage distributionPackage, final File distributionFolder,
            final List<ArtifactWithSettings> bundleArtifacts) {
        this.environment = environment;
        this.distributionPackage = distributionPackage;
        this.distributionFolder = distributionFolder;
        this.bundleArtifacts = bundleArtifacts;
    }

    public List<ArtifactWithSettings> getBundleArtifacts() {
        return bundleArtifacts;
    }

    public File getDistributionFolder() {
        return distributionFolder;
    }

    public DistributionPackage getDistributionPackage() {
        return distributionPackage;
    }

    public EnvironmentConfiguration getEnvironment() {
        return environment;
    }

    public void setBundleArtifacts(final List<ArtifactWithSettings> bundleArtifacts) {
        this.bundleArtifacts = bundleArtifacts;
    }

    public void setDistributionFolder(final File distributionFolder) {
        this.distributionFolder = distributionFolder;
    }

    public void setDistributionPackage(final DistributionPackage distributionPackage) {
        this.distributionPackage = distributionPackage;
    }

    public void setEnvironment(final EnvironmentConfiguration environment) {
        this.environment = environment;
    }
}
