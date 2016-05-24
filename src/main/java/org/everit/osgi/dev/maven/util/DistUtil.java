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
        .replace(" ", "\\ ")
        .replace(";", "\\;"); // mac osx command line escaping
  }

  public String propertyKey(final String key) {
    return escape(PropertiesEscape.escapePropertiesKey(key));
  }

  public String propertyValue(final String value) {
    return escape(PropertiesEscape.escapePropertiesValue(value));
  }

}
