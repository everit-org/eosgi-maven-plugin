package org.everit.osgi.dev.maven.util;


public class DuplicateArtifactException extends RuntimeException {

    private ArtifactKey artifactKey;

    public DuplicateArtifactException(ArtifactKey artifactKey) {
        super("The artifact is listed more than once: " + artifactKey.toString());
        this.artifactKey = artifactKey;
    }

    public ArtifactKey getArtifactKey() {
        return artifactKey;
    }
}
