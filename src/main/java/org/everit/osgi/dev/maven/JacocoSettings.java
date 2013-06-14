package org.everit.osgi.dev.maven;


/*
 * Copyright (c) 2011, Everit Kft.
 *
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

public class JacocoSettings {

    /**
     * @parameter
     */
    private boolean append = true;

    /**
     * @parameter
     */
    private String includes;

    /**
     * @parameter
     */
    private String excludes;

    /**
     * @parameter
     */
    private boolean dumponexit = true;


    public boolean isAppend() {
        return append;
    }

    public void setAppend(boolean append) {
        this.append = append;
    }

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public boolean isDumponexit() {
        return dumponexit;
    }

    public void setDumponexit(boolean dumponexit) {
        this.dumponexit = dumponexit;
    }

    @Override
    public String toString() {
        return "JacocoSettings [append=" + append + ", includes=" + includes + ", excludes="
                + excludes + ", dumponexit=" + dumponexit + "]";
    }

}
