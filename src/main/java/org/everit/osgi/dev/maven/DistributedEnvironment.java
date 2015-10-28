/*
 * Copyright (C) 2011 Everit Kft. (http://everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.osgi.dev.maven;

import java.io.File;
import java.util.List;

import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;

/**
 * Metadata of a distributed environment.
 */
public class DistributedEnvironment {

  private List<DistributableArtifact> distributableArtifacts;

  private File distributionFolder;

  private DistributionPackageType distributionPackage;

  private EnvironmentConfiguration environment;

  public DistributedEnvironment() {
  }

  /**
   * Constructor.
   */
  public DistributedEnvironment(final EnvironmentConfiguration environment,
      final DistributionPackageType distributionPackage, final File distributionFolder,
      final List<DistributableArtifact> bundleArtifacts) {
    this.environment = environment;
    this.distributionPackage = distributionPackage;
    this.distributionFolder = distributionFolder;
    distributableArtifacts = bundleArtifacts;
  }

  public List<DistributableArtifact> getDistributableArtifacts() {
    return distributableArtifacts;
  }

  public File getDistributionFolder() {
    return distributionFolder;
  }

  public DistributionPackageType getDistributionPackage() {
    return distributionPackage;
  }

  public EnvironmentConfiguration getEnvironment() {
    return environment;
  }

  public void setDistributableArtifacts(final List<DistributableArtifact> bundleArtifacts) {
    distributableArtifacts = bundleArtifacts;
  }

  public void setDistributionFolder(final File distributionFolder) {
    this.distributionFolder = distributionFolder;
  }

  public void setDistributionPackage(final DistributionPackageType distributionPackage) {
    this.distributionPackage = distributionPackage;
  }

  public void setEnvironment(final EnvironmentConfiguration environment) {
    this.environment = environment;
  }

}
