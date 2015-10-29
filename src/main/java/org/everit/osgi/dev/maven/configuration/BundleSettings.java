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
package org.everit.osgi.dev.maven.configuration;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Extra settings for a bundle that will be installed to a specific environment.
 */
public class BundleSettings {

  /**
   * The start level of the bundle or if left empty, the framework default startlevel will be used.
   *
   */
  @Parameter
  private Integer startLevel;

  /**
   * The Bundle-SymbolicName, a required parameter.
   *
   */
  @Parameter
  private String symbolicName;

  /**
   * The version of the bundle. If left empty, all bundles with the specified symbolic name will be
   * relevant. At the moment only exact values are suppoted, range support may come in a future
   * version if requested by many users.
   *
   */
  @Parameter
  private String version;

  public Integer getStartLevel() {
    return startLevel;
  }

  public String getSymbolicName() {
    return symbolicName;
  }

  public String getVersion() {
    return version;
  }

  public void setStartLevel(final Integer startLevel) {
    this.startLevel = startLevel;
  }

  public void setSymbolicName(final String symbolicName) {
    this.symbolicName = symbolicName;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return "BundleSettings [symbolicName=" + symbolicName + ", version=" + version + ", startLevel="
        + startLevel
        + "]";
  }

}
