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

import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;

/**
 * A Maven artifact that has information about potential OSGi headers.
 */
public class DistributableArtifact {

    /**
     * The maven artifact.
     */
    private Artifact artifact;

    /**
     * The global manifest of the jar.
     */
    private Manifest manifest;

    private DistributableArtifactBundleMeta bundle;

    public DistributableArtifact(final Artifact artifact, final Manifest manifest,
            final DistributableArtifactBundleMeta bundleMeta) {
        this.artifact = artifact;
        this.manifest = manifest;
        this.bundle = bundleMeta;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public DistributableArtifactBundleMeta getBundle() {
        return bundle;
    }
}
