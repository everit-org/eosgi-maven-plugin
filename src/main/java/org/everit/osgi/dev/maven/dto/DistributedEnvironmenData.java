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
import java.util.Collection;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.everit.osgi.dev.dist.util.configuration.schema.EnvironmentType;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;

/**
 * Metadata of a distributed environment.
 */
public class DistributedEnvironmenData {

  private Collection<Artifact> distributableArtifacts;

  private EnvironmentType distributedEnvironment;

  private File distributionFolder;

  private EnvironmentConfiguration environment;

  public DistributedEnvironmenData() {
  }

  /**
   * Constructor.
   */
  public DistributedEnvironmenData(final EnvironmentConfiguration environment,
      final EnvironmentType distributedEnvironment, final File distributionFolder,
      final Collection<Artifact> bundleArtifacts) {
    this.environment = environment;
    this.distributedEnvironment = distributedEnvironment;
    this.distributionFolder = distributionFolder;
    distributableArtifacts = bundleArtifacts;
  }

  public Collection<Artifact> getDistributableArtifacts() {
    return distributableArtifacts;
  }

  public EnvironmentType getDistributedEnvironment() {
    return distributedEnvironment;
  }

  public File getDistributionFolder() {
    return distributionFolder;
  }

  public EnvironmentConfiguration getEnvironment() {
    return environment;
  }

  public void setDistributableArtifacts(final List<Artifact> bundleArtifacts) {
    distributableArtifacts = bundleArtifacts;
  }

  public void setDistributedEnvironment(final EnvironmentType distributedEnvironment) {
    this.distributedEnvironment = distributedEnvironment;
  }

  public void setDistributionFolder(final File distributionFolder) {
    this.distributionFolder = distributionFolder;
  }

  public void setEnvironment(final EnvironmentConfiguration environment) {
    this.environment = environment;
  }

}
