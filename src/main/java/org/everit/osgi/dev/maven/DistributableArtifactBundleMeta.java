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
package org.everit.osgi.dev.maven;

/**
 * Metadata class for a bundle that will be installed on the target OSGi container.
 */
public class DistributableArtifactBundleMeta {

  private final String exportPackage;

  private final String fragmentHost;

  private final String importPackage;

  private final Integer startLevel;

  private final String symbolicName;

  private final String version;

  /**
   * Constructor.
   */
  public DistributableArtifactBundleMeta(final String symbolicName, final String version,
      final String fragmentHost,
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

  public boolean hasFragmentHost() {
    return (fragmentHost != null) && !fragmentHost.trim().isEmpty();
  }

  public boolean hasStartLevel() {
    return startLevel != null;
  }

}
