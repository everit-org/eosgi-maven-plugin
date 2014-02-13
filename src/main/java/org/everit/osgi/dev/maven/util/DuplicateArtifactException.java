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
package org.everit.osgi.dev.maven.util;


public class DuplicateArtifactException extends RuntimeException {

    private ArtifactKey artifactKey;

    public DuplicateArtifactException(ArtifactKey artifactKey) {
        super("The artifact is listed more than once: " + artifactKey.toString());
        this.artifactKey = artifactKey;
    }

    public ArtifactKey getArtifactKey() {
        return artifactKey;
    }
}
