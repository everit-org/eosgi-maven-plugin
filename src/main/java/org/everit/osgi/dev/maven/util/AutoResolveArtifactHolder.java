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
package org.everit.osgi.dev.maven.util;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactRequest;

/**
 * A holder class for aether artifacts that will resolve the artifact as soon as it is requested.
 */
public class AutoResolveArtifactHolder {

  private Artifact artifact;

  private final PredefinedRepoArtifactResolver artifactResolver;

  public AutoResolveArtifactHolder(final Artifact artifact,
      final PredefinedRepoArtifactResolver artifactResolver) {
    this.artifact = artifact;
    this.artifactResolver = artifactResolver;
  }

  /**
   * Returns the embedded artifact and resolves it if necessary.
   *
   * @return The resolved artifact.
   * @throws MojoExecutionException
   *           if anything happens.
   */
  public synchronized Artifact getResolvedArtifact() throws MojoExecutionException {
    if (artifact.getFile() != null) {
      return artifact;
    }

    ArtifactRequest artifactRequest = new ArtifactRequest();
    artifactRequest.setArtifact(artifact);
    this.artifact = artifactResolver.resolve(artifactRequest);
    return this.artifact;
  }
}
