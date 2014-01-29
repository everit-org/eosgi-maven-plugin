package org.everit.osgi.dev.maven;

import org.apache.maven.plugins.annotations.Parameter;

public class BundleSettings {

    /**
     * The start level of the bundle or if left empty, the framework default startlevel will be used.
     * 
     */
    @Parameter
    private Integer startLevel;

    /**
     * The Bundle-SymbolicName, a required parameter.
     * 
     */
    @Parameter
    private String symbolicName;

    /**
     * The version of the bundle. If left empty, all bundles with the specified symbolic name will be relevant. At the
     * moment only exact values are suppoted, range support may come in a future version if requested by many users.
     * 
     */
    @Parameter
    private String version;

    public Integer getStartLevel() {
        return startLevel;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }

    public void setStartLevel(final Integer startLevel) {
        this.startLevel = startLevel;
    }

    public void setSymbolicName(final String symbolicName) {
        this.symbolicName = symbolicName;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BundleSettings [symbolicName=" + symbolicName + ", version=" + version + ", startLevel=" + startLevel
                + "]";
    }

}
