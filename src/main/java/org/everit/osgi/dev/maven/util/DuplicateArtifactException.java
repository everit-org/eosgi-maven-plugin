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

/**
 * Exception showing that an artifact is twice in the dependency list.
 */
public class DuplicateArtifactException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ArtifactKey artifactKey;

  public DuplicateArtifactException(final ArtifactKey artifactKey) {
    super("The artifact is listed more than once: " + artifactKey.toString());
    this.artifactKey = artifactKey;
  }

  public ArtifactKey getArtifactKey() {
    return artifactKey;
  }
}
