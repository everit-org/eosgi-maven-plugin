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
import java.util.List;
import java.util.Map;

import javax.annotation.Generated;

import org.apache.maven.plugins.annotations.Parameter;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;

/**
 * The OSGi environment configurations that can be overridden.
 */
public class EnvironmentOverride {

  /**
   * System properties that will be added to the JVM of started OSGI container.
   */
  @Parameter
  private Map<String, String> systemProperties = new HashMap<>();

  /**
   * Defines the case where these overrides must be used.
   */
  @Parameter(required = true)
  private UseByType useBy;

  /**
   * The JVM options that will be applied during starting the OSGI Container.
   */
  @Parameter
  private List<String> vmOptions;

  public Map<String, String> getSystemProperties() {
    return systemProperties;
  }

  public UseByType getUseBy() {
    return useBy;
  }

  public List<String> getVmOptions() {
    return vmOptions;
  }

  public void setSystemProperties(final Map<String, String> systemProperties) {
    this.systemProperties = systemProperties;
  }

  public void setUseBy(final UseByType useBy) {
    this.useBy = useBy;
  }

  public void setVmOptions(final List<String> vmOptions) {
    this.vmOptions = vmOptions;
  }

  @Override
  @Generated("eclipse")
  public String toString() {
    return "EnvironmentOverride [systemProperties=" + systemProperties + ", useBy=" + useBy
        + ", vmOptions=" + vmOptions + "]";
  }

}
