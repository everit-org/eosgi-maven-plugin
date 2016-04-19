/*
 * Copyright (C) 2011 Everit Kft. (http://everit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.osgi.dev.maven.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.unbescape.properties.PropertiesEscape;

/**
 * Utility methods for distribution mojo.
 */
public final class DistUtil {

  private String escape(final String value) {
    if (value == null) {
      return null;
    }
    return value
        .replace(",", "\\,")
        .replace("=", "\\=")
        .replace(" ", "\\ ");
    // TODO mac command line escape.replace(";", "\\;")
  }

  /**
   * Creates an ordered map that is sorted by the start level of the bundles that should be
   * installed.
   *
   * @param artifacts
   *          The OSGi bundle artifacts.
   * @param defaultStartLevel
   *          The default startlevel of the OSGi environment.
   * @param osgiAction
   *          Additional action for the bundle.
   * @return The ordered map.
   */
  public TreeMap<Integer, List<ArtifactType>> getBundleArtifactsOrderedByStartLevel(
      final Collection<ArtifactType> artifacts,
      final int defaultStartLevel, final String osgiAction) {

    TreeMap<Integer, List<ArtifactType>> result = new TreeMap<>();
    for (ArtifactType artifact : artifacts) {
      BundleDataType bundle = artifact.getBundle();
      if ((bundle != null)
          && ((osgiAction == null) || bundle.getAction().value().equals(osgiAction))) {
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
    return escape(PropertiesEscape.escapePropertiesKey(key));
  }

  public String propertyValue(final String value) {
    return escape(PropertiesEscape.escapePropertiesValue(value));
  }

}
