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

import javax.annotation.Generated;

import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;

/**
 * Key of a maven artifact.
 */
public class ArtifactKey {

  private final String artifactId;

  private final String classifier;

  private final String groupId;

  private final String targetFile;

  private final String targetFolder;

  private final String type;

  private final String version;

  /**
   * Constructor.
   */
  public ArtifactKey(final ArtifactType artifact) {
    groupId = artifact.getGroupId();
    artifactId = artifact.getArtifactId();
    version = artifact.getVersion();
    type = artifact.getType();
    classifier = artifact.getClassifier();
    targetFolder = artifact.getTargetFolder();
    targetFile = artifact.getTargetFile();
  }

  @Override
  @Generated("eclipse")
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ArtifactKey other = (ArtifactKey) obj;
    if (artifactId == null) {
      if (other.artifactId != null) {
        return false;
      }
    } else if (!artifactId.equals(other.artifactId)) {
      return false;
    }
    if (classifier == null) {
      if (other.classifier != null) {
        return false;
      }
    } else if (!classifier.equals(other.classifier)) {
      return false;
    }
    if (groupId == null) {
      if (other.groupId != null) {
        return false;
      }
    } else if (!groupId.equals(other.groupId)) {
      return false;
    }
    if (targetFile == null) {
      if (other.targetFile != null) {
        return false;
      }
    } else if (!targetFile.equals(other.targetFile)) {
      return false;
    }
    if (targetFolder == null) {
      if (other.targetFolder != null) {
        return false;
      }
    } else if (!targetFolder.equals(other.targetFolder)) {
      return false;
    }
    if (type == null) {
      if (other.type != null) {
        return false;
      }
    } else if (!type.equals(other.type)) {
      return false;
    }
    if (version == null) {
      if (other.version != null) {
        return false;
      }
    } else if (!version.equals(other.version)) {
      return false;
    }
    return true;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getClassifier() {
    return classifier;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getTargetFile() {
    return targetFile;
  }

  public String getTargetFolder() {
    return targetFolder;
  }

  public String getType() {
    return type;
  }

  public String getVersion() {
    return version;
  }

  @Override
  @Generated("eclipse")
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = (prime * result) + ((artifactId == null) ? 0 : artifactId.hashCode());
    result = (prime * result) + ((classifier == null) ? 0 : classifier.hashCode());
    result = (prime * result) + ((groupId == null) ? 0 : groupId.hashCode());
    result = (prime * result) + ((targetFile == null) ? 0 : targetFile.hashCode());
    result = (prime * result) + ((targetFolder == null) ? 0 : targetFolder.hashCode());
    result = (prime * result) + ((type == null) ? 0 : type.hashCode());
    result = (prime * result) + ((version == null) ? 0 : version.hashCode());
    return result;
  }

  @Override
  @Generated("eclipse")
  public String toString() {
    return "ArtifactKey [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
        + ", type=" + type + ", classifier=" + classifier + ", targetFolder=" + targetFolder
        + ", targetFile=" + targetFile
        + "]";
  }
}
