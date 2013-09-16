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

public class DistributedBundleArtifact {

    private BundleArtifact bundleArtifact;

    private Integer startLevel;

    public void setBundleArtifact(BundleArtifact bundleArtifact) {
        this.bundleArtifact = bundleArtifact;
    }

    public Artifact getArtifact() {
        return bundleArtifact.getArtifact();
    }

    public String getExportPackage() {
        return bundleArtifact.getExportPackage();
    }

    public String getImportPackage() {
        return bundleArtifact.getImportPackage();
    }

    public String getSymbolicName() {
        return bundleArtifact.getSymbolicName();
    }

    public String getVersion() {
        return bundleArtifact.getVersion();
    }

    public Manifest getManifest() {
        return bundleArtifact.getManifest();
    }

    public String getFragmentHost() {
        return bundleArtifact.getFragmentHost();
    }

    public void setStartLevel(Integer startLevel) {
        this.startLevel = startLevel;
    }

    public Integer getStartLevel() {
        return startLevel;
    }

}
