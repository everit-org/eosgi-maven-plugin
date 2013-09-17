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

import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;

/**
 * A Maven artifact that is an OSGI bundle as well.
 */
public class BundleArtifact {

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

}
