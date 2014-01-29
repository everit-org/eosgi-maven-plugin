package org.everit.osgi.dev.maven;

import org.apache.maven.plugins.annotations.Parameter;

public class JacocoSettings {

    @Parameter
    private boolean append = true;

    @Parameter
    private boolean dumponexit = true;

    @Parameter
    private String excludes;

    @Parameter
    private String includes;

    public String getExcludes() {
        return excludes;
    }

    public String getIncludes() {
        return includes;
    }

    public boolean isAppend() {
        return append;
    }

    public boolean isDumponexit() {
        return dumponexit;
    }

    public void setAppend(final boolean append) {
        this.append = append;
    }

    public void setDumponexit(final boolean dumponexit) {
        this.dumponexit = dumponexit;
    }

    public void setExcludes(final String excludes) {
        this.excludes = excludes;
    }

    public void setIncludes(final String includes) {
        this.includes = includes;
    }

    @Override
    public String toString() {
        return "JacocoSettings [append=" + append + ", includes=" + includes + ", excludes=" + excludes
                + ", dumponexit=" + dumponexit + "]";
    }

}
