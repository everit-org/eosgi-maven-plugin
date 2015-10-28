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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * The OSGI environment that is specified in the pom.xml for the plugin. *
 */
public class EnvironmentConfiguration {

  private static final int DEFAULT_SHUTDOWN_TIMEOUT = 30000;

  private static final int DEFAULT_TEST_RUN_TIMEOUT = 180000;

  /**
   * Setting non-default behaviors for bundles. For more information see the javadoc of
   * {@link BundleSettings} class.
   */
  @Parameter
  private List<BundleSettings> bundleSettings = new ArrayList<BundleSettings>();

  /**
   * The default startlevel for newly installed bundles.
   */
  @Parameter
  private Integer bundleStartLevel;

  /**
   * The name of the osgi framework. Currently equinox is supported. Default is equinox.
   */
  @Parameter
  private String framework;

  /**
   * The default start level of the OSGi framework.
   */
  @Parameter
  private Integer frameworkStartLevel;

  /**
   * The id that will be used to identify this configuration in system property of the framework.
   */
  @Parameter
  private String id;

  /**
   * The amount of time in milliseconds until the plugin waits for the environment to stop after a
   * CTRL+C was sent. Default value is half a minute.
   */
  @Parameter(defaultValue = "30000")
  private int shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;

  /**
   * System properties that will be added to the JVM of started OSGI container.
   */
  @Parameter
  private Map<String, String> systemProperties = new HashMap<String, String>();

  /**
   * The timeout in milliseconds after the Tests should be finished for sure. The environment will
   * be stopped when this exceeds. Default value is five minutes.
   */
  @Parameter(defaultValue = "180000")
  private int timeout = DEFAULT_TEST_RUN_TIMEOUT;

  /**
   * The JVM options that will be applied during starting the OSGI Container.
   */
  @Parameter
  private List<String> vmOptions;

  public List<BundleSettings> getBundleSettings() {
    return bundleSettings;
  }

  public Integer getBundleStartLevel() {
    return bundleStartLevel;
  }

  public String getFramework() {
    return framework;
  }

  public Integer getFrameworkStartLevel() {
    return frameworkStartLevel;
  }

  public String getId() {
    return id;
  }

  public int getShutdownTimeout() {
    return shutdownTimeout;
  }

  public Map<String, String> getSystemProperties() {
    return systemProperties;
  }

  public int getTimeout() {
    return timeout;
  }

  public List<String> getVmOptions() {
    return vmOptions;
  }

  public void setBundleSettings(final List<BundleSettings> bundleSettings) {
    this.bundleSettings = bundleSettings;
  }

  public void setBundleStartLevel(final Integer bundleStartLevel) {
    this.bundleStartLevel = bundleStartLevel;
  }

  public void setFramework(final String framework) {
    this.framework = framework;
  }

  public void setFrameworkStartLevel(final Integer frameworkStartLevel) {
    this.frameworkStartLevel = frameworkStartLevel;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setShutdownTimeout(final int shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
  }

  public void setSystemProperties(final Map<String, String> systemProperties) {
    this.systemProperties = systemProperties;
  }

  public void setTimeout(final int timeout) {
    this.timeout = timeout;
  }

  public void setVmOptions(final List<String> vmOptions) {
    this.vmOptions = vmOptions;
  }

  @Override
  public String toString() {
    return "EnvironmentConfiguration [id=" + id + ", framework=" + framework
        + ", frameworkStartLevel="
        + frameworkStartLevel + ", timeout=" + timeout + ", shutdownTimeout=" + shutdownTimeout
        + ", bundleStartLevel=" + bundleStartLevel + ", bundleSettings=" + bundleSettings
        + ", systemProperties=" + systemProperties + ", vmOptions=" + vmOptions + "]";
  }

}
