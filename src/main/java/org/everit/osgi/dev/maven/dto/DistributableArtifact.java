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
import java.util.jar.Manifest;

import org.everit.osgi.dev.maven.configuration.EOSGiArtifact;

/**
 * A Maven artifact that has information about potential OSGi headers.
 */
public class DistributableArtifact {

  /**
   * The maven artifact.
   */
  private final EOSGiArtifact artifact;

  private final File artifactFile;

  private final DistributableArtifactBundleMeta bundle;

  /**
   * The global manifest of the jar.
   */
  private final Manifest manifest;

  /**
   * Constructor.
   */
  public DistributableArtifact(final EOSGiArtifact artifact, final File artifactFile,
      final Manifest manifest, final DistributableArtifactBundleMeta bundleMeta) {
    this.artifact = artifact;
    this.manifest = manifest;
    this.bundle = bundleMeta;
    this.artifactFile = artifactFile;
  }

  public EOSGiArtifact getArtifact() {
    return artifact;
  }

  public File getArtifactFile() {
    return artifactFile;
  }

  public DistributableArtifactBundleMeta getBundle() {
    return bundle;
  }

  public Manifest getManifest() {
    return manifest;
  }
}
