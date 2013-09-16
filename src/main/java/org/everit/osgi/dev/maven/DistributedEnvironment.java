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
import java.util.List;

import org.everit.osgi.dev.maven.jaxb.dist.definition.DistributionPackage;

public class DistributedEnvironment {

    private EnvironmentConfiguration environment;

    private DistributionPackage distributionPackage;

    private File distributionFolder;

    private List<DistributedBundleArtifact> bundleArtifacts;

    public DistributedEnvironment() {
    }

    public DistributedEnvironment(EnvironmentConfiguration environment, DistributionPackage distributionPackage,
            File distributionFolder, List<DistributedBundleArtifact> bundleArtifacts) {
        this.environment = environment;
        this.distributionPackage = distributionPackage;
        this.distributionFolder = distributionFolder;
        this.bundleArtifacts = bundleArtifacts;
    }

    public EnvironmentConfiguration getEnvironment() {
        return environment;
    }

    public void setEnvironment(EnvironmentConfiguration environment) {
        this.environment = environment;
    }

    public DistributionPackage getDistributionPackage() {
        return distributionPackage;
    }

    public void setDistributionPackage(DistributionPackage distributionPackage) {
        this.distributionPackage = distributionPackage;
    }

    public File getDistributionFolder() {
        return distributionFolder;
    }

    public void setDistributionFolder(File distributionFolder) {
        this.distributionFolder = distributionFolder;
    }

    public List<DistributedBundleArtifact> getBundleArtifacts() {
        return bundleArtifacts;
    }

    public void setBundleArtifacts(List<DistributedBundleArtifact> bundleArtifacts) {
        this.bundleArtifacts = bundleArtifacts;
    }
}
