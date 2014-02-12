package org.everit.osgi.dev.maven.util;

import org.everit.osgi.dev.maven.jaxb.dist.definition.Artifact;

public class ArtifactKey {

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final String type;

    private final String classifier;

    private final String targetFolder;

    private final String targetFile;

    public ArtifactKey(final Artifact artifact) {
        this.groupId = artifact.getGroupId();
        this.artifactId = artifact.getArtifactId();
        this.version = artifact.getVersion();
        this.type = artifact.getType();
        this.classifier = artifact.getClassifier();
        this.targetFolder = artifact.getTargetFolder();
        this.targetFile = artifact.getTargetFile();
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getType() {
        return type;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getTargetFolder() {
        return targetFolder;
    }

    public String getTargetFile() {
        return targetFile;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((classifier == null) ? 0 : classifier.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((targetFile == null) ? 0 : targetFile.hashCode());
        result = prime * result + ((targetFolder == null) ? 0 : targetFolder.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ArtifactKey other = (ArtifactKey) obj;
        if (artifactId == null) {
            if (other.artifactId != null)
                return false;
        } else if (!artifactId.equals(other.artifactId))
            return false;
        if (classifier == null) {
            if (other.classifier != null)
                return false;
        } else if (!classifier.equals(other.classifier))
            return false;
        if (groupId == null) {
            if (other.groupId != null)
                return false;
        } else if (!groupId.equals(other.groupId))
            return false;
        if (targetFile == null) {
            if (other.targetFile != null)
                return false;
        } else if (!targetFile.equals(other.targetFile))
            return false;
        if (targetFolder == null) {
            if (other.targetFolder != null)
                return false;
        } else if (!targetFolder.equals(other.targetFolder))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ArtifactKey [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", type="
                + type + ", classifier=" + classifier + ", targetFolder=" + targetFolder + ", targetFile=" + targetFile
                + "]";
    }
}
