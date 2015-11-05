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

import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * The abstract configuration of the launched OSGi Container.
 */
public abstract class AbstractLaunchConfig {

  /**
   * The Jacoco code coverage generation settings. To see the possible settings see
   * {@link JacocoConfiguration}.
   */
  @Parameter
  private JacocoSettings jacoco;

  /**
   * The program arguments that will be applied during starting the OSGI Container.
   */
  @Parameter
  protected Map<String, String> programArguments = new HashMap<>();

  /**
   * System properties that will be added to the JVM of started OSGI container.
   */
  @Parameter
  protected Map<String, String> systemProperties = new HashMap<>();

  /**
   * The JVM arguments that will be applied during starting the OSGI Container.
   */
  @Parameter
  protected Map<String, String> vmArguments = new HashMap<>();

  /**
   * Returns the configured jacoco settings as a map.
   */
  public Map<String, String> getJacocoSettingsMap() {
    if (jacoco == null) {
      return null;
    }
    return jacoco.toMap();
  }

  public Map<String, String> getProgramArguments() {
    return programArguments;
  }

  public Map<String, String> getSystemProperties() {
    return systemProperties;
  }

  public Map<String, String> getVmArguments() {
    return vmArguments;
  }

}
