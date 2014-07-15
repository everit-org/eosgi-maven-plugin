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
package org.everit.osgi.dev.maven;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Settings for jacoco. For more information, please see {@link http://www.eclemma.org/jacoco/trunk/doc/agent.html}.
 *
 */
public class JacocoSettings {

    @Parameter
    private String address;

    @Parameter
    private boolean append = true;

    @Parameter
    private boolean dumponexit = true;

    @Parameter
    private String excludes;

    @Parameter
    private String includes;

    @Parameter
    private String output;

    @Parameter
    private Integer port;

    public String getAddress() {
        return address;
    }

    public String getExcludes() {
        return excludes;
    }

    public String getIncludes() {
        return includes;
    }

    public String getOutput() {
        return output;
    }

    public Integer getPort() {
        return port;
    }

    public boolean isAppend() {
        return append;
    }

    public boolean isDumponexit() {
        return dumponexit;
    }

    public void setAddress(final String address) {
        this.address = address;
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

    public void setOutput(final String output) {
        this.output = output;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "JacocoSettings [append=" + append + ", includes=" + includes + ", excludes=" + excludes
                + ", dumponexit=" + dumponexit + "]";
    }

}
