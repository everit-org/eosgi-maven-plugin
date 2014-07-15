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

public class DistributableArtifactBundleMeta {

    private final String exportPackage;

    private final String fragmentHost;

    private final String importPackage;

    private final Integer startLevel;

    private final String symbolicName;

    private final String version;

    public DistributableArtifactBundleMeta(final String symbolicName, final String version, final String fragmentHost,
            final String importPackage,
            final String exportPackage,
            final Integer startLevel) {
        this.symbolicName = symbolicName;
        this.version = version;
        this.fragmentHost = fragmentHost;
        this.importPackage = importPackage;
        this.exportPackage = exportPackage;
        this.startLevel = startLevel;
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

    public Integer getStartLevel() {
        return startLevel;
    }

    public String getSymbolicName() {
        return symbolicName;
    }

    public String getVersion() {
        return version;
    }
}
