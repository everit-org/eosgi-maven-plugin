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
package org.everit.osgi.dev.maven.configuration;

import javax.annotation.Generated;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Dependency of the environment.
 */
public class EOSGiArtifact {

  @Parameter
  private String artifactId;

  @Parameter
  private String classifier;

  @Parameter
  private String groupId;

  @Parameter
  private String type;

  @Parameter
  private String version;

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
    EOSGiArtifact other = (EOSGiArtifact) obj;
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
    result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
    result = prime * result + ((classifier == null) ? 0 : classifier.hashCode());
    result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    return result;
  }

  public void setArtifactId(final String artifactId) {
    this.artifactId = artifactId;
  }

  public void setClassifier(final String classifier) {
    this.classifier = classifier;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

}
