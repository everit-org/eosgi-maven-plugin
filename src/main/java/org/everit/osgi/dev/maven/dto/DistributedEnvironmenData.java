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
package org.everit.osgi.dev.maven.dto;

import java.io.File;
import java.util.List;

import org.everit.osgi.dev.eosgi.dist.schema.xsd.EnvironmentType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;

/**
 * Metadata of a distributed environment.
 */
public class DistributedEnvironmenData {

  private List<DistributableArtifact> distributableArtifacts;

  private File distributionFolder;

  private EnvironmentType distributedEnvironment;

  private EnvironmentConfiguration environment;

  public DistributedEnvironmenData() {
  }

  /**
   * Constructor.
   */
  public DistributedEnvironmenData(final EnvironmentConfiguration environment,
      final EnvironmentType distributedEnvironment, final File distributionFolder,
      final List<DistributableArtifact> bundleArtifacts) {
    this.environment = environment;
    this.distributedEnvironment = distributedEnvironment;
    this.distributionFolder = distributionFolder;
    distributableArtifacts = bundleArtifacts;
  }

  public List<DistributableArtifact> getDistributableArtifacts() {
    return distributableArtifacts;
  }

  public File getDistributionFolder() {
    return distributionFolder;
  }

  public EnvironmentType getDistributedEnvironment() {
    return distributedEnvironment;
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

  public void setDistributedEnvironment(final EnvironmentType distributedEnvironment) {
    this.distributedEnvironment = distributedEnvironment;
  }

  public void setEnvironment(final EnvironmentConfiguration environment) {
    this.environment = environment;
  }

}