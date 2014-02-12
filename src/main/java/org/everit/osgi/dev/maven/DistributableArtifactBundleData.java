package org.everit.osgi.dev.maven;

public class DistributableArtifactBundleData {

    private final String symbolicName;

    private final String version;

    private final String fragmentHost;

    private final String importPackage;

    private final String exportPackage;

    private final Integer startLevel;

    public DistributableArtifactBundleData(String symbolicName, String version, String fragmentHost,
            String importPackage,
            String exportPackage,
            Integer startLevel) {
        this.symbolicName = symbolicName;
        this.version = version;
        this.fragmentHost = fragmentHost;
        this.importPackage = importPackage;
        this.exportPackage = exportPackage;
        this.startLevel = startLevel;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }

    public String getFragmentHost() {
        return fragmentHost;
    }

    public String getImportPackage() {
        return importPackage;
    }

    public String getExportPackage() {
        return exportPackage;
    }

    public Integer getStartLevel() {
        return startLevel;
    }
}
