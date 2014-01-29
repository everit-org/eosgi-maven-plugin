package org.everit.osgi.dev.maven;

import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;

/**
 * A Maven artifact that has information about potential OSGi headers.
 */
public class ProcessedArtifact {

    /**
     * The maven artifact.
     */
    private Artifact artifact;

    /**
     * Export-Package entry of the bundle.
     */
    private String exportPackage;

    /**
     * The fragment host header if one exists.
     */
    private String fragmentHost;

    /**
     * Import-Package entry of the bundle.
     */
    private String importPackage;

    /**
     * The global manifest of the jar.
     */
    private Manifest manifest;

    /**
     * The Bundle-SymbolicName.
     */
    private String symbolicName;

    /**
     * The Bundle-Version.
     */
    private String version;

    public Artifact getArtifact() {
        return artifact;
    }

    public String getExportPackage() {
        return exportPackage;
    }

    public String getFragmentHost() {
        return fragmentHost;
    }

    public String getImportPackage() {
        return importPackage;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }

    public void setArtifact(final Artifact artifact) {
        this.artifact = artifact;
    }

    public void setExportPackage(final String exportPackage) {
        this.exportPackage = exportPackage;
    }

    public void setFragmentHost(final String fragmentHost) {
        this.fragmentHost = fragmentHost;
    }

    public void setImportPackage(final String importPackage) {
        this.importPackage = importPackage;
    }

    public void setManifest(final Manifest manifest) {
        this.manifest = manifest;
    }

    public void setSymbolicName(final String symbolicName) {
        this.symbolicName = symbolicName;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public boolean isOSGiBundle() {
        return symbolicName != null && version != null;
    }

}
