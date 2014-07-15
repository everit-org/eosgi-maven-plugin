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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import org.apache.velocity.tools.generic.EscapeTool;
import org.everit.osgi.dev.maven.jaxb.dist.definition.ArtifactType;
import org.everit.osgi.dev.maven.jaxb.dist.definition.BundleDataType;

public final class DistUtil {

    private final EscapeTool escapeTool = new EscapeTool();

    public TreeMap<Integer, List<ArtifactType>> getBundleArtifactsOrderedByStartLevel(
            final Collection<ArtifactType> artifacts,
            final int defaultStartLevel, final String osgiAction) {

        TreeMap<Integer, List<ArtifactType>> result = new TreeMap<>();
        for (ArtifactType artifact : artifacts) {
            BundleDataType bundle = artifact.getBundle();
            if ((bundle != null) && ((osgiAction == null) || bundle.getAction().value().equals(osgiAction))) {
                int startLevel = defaultStartLevel;
                if (bundle.getStartLevel() != null) {
                    startLevel = bundle.getStartLevel();
                }
                List<ArtifactType> bundleArtifacts = result.get(startLevel);
                if (bundleArtifacts == null) {
                    bundleArtifacts = new ArrayList<>();
                    result.put(startLevel, bundleArtifacts);
                }
                bundleArtifacts.add(artifact);
            }
        }
        return result;
    }

    public String propertyKey(final String key) {
        return escapeTool.propertyKey(key).replace(",", "\\,");
    }

    public String propertyValue(final String value) {
        return escapeTool.propertyKey(value).replace(",", "\\,");
    }
}
