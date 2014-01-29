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

public class ArtifactWithSettings {

    private ProcessedArtifact processedArtifact;

    private Integer startLevel;

    public Artifact getArtifact() {
        return processedArtifact.getArtifact();
    }

    public String getExportPackage() {
        return processedArtifact.getExportPackage();
    }

    public String getFragmentHost() {
        return processedArtifact.getFragmentHost();
    }

    public String getImportPackage() {
        return processedArtifact.getImportPackage();
    }

    public Manifest getManifest() {
        return processedArtifact.getManifest();
    }

    public Integer getStartLevel() {
        return startLevel;
    }

    public String getSymbolicName() {
        return processedArtifact.getSymbolicName();
    }

    public String getVersion() {
        return processedArtifact.getVersion();
    }

    public void setBundleArtifact(final ProcessedArtifact bundleArtifact) {
        this.processedArtifact = bundleArtifact;
    }

    public void setStartLevel(final Integer startLevel) {
        this.startLevel = startLevel;
    }

}