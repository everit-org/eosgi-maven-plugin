/**
 * This file is part of Everit - Maven OSGi plugin.
 *
 * Everit - Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven.util;

import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactType;

public class ArtifactKey {

    private final String artifactId;

    private final String classifier;

    private final String groupId;

    private final String targetFile;

    private final String targetFolder;

    private final String type;

    private final String version;

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
    public String toString() {
        return "ArtifactKey [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", type="
                + type + ", classifier=" + classifier + ", targetFolder=" + targetFolder + ", targetFile=" + targetFile
                + "]";
    }
}
