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

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * The OSGI environment that is specified in the pom.xml for the plugin.
 */
public class EnvironmentConfiguration {

  private static final int DEFAULT_SHUTDOWN_TIMEOUT = 30000;

  private static final int DEFAULT_TEST_RUN_TIMEOUT = 180000;

  @Parameter
  private List<EOSGiArtifact> artifacts;

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
   * The default startlevel for newly installed bundles.
   */
  @Parameter
  private Integer initialBundleStartLevel;

  /**
   * The configuration of the launched OSGi Container.
   */
  @Parameter
  private LaunchConfig launchConfig;

  /**
   * Regular expressions of paths that should be skipped during the cleanup process in the end of
   * the distribution. URI-s are relative to the root folder of the environment and directory names
   * end with slash. Slash is used as directory separators. During the clean up process all files
   * are removed that are not created during the distribution. E.g.:
   *
   * <pre>
   * &lt;runtimePathRegexes&gt;
   *    &lt;log&gt;log/&lt;/log&gt;
   *    &lt;errorLogs&gt;configuration/.*\.log&lt;/errorLogs&gt;
   *  &lt;/runtimePaths&gt;
   * </pre>
   */
  @Parameter
  private Map<String, String> runtimePathRegexes;

  /**
   * The amount of time in milliseconds until the plugin waits for the environment to stop after a
   * CTRL+C was sent. Default value is half a minute.
   */
  @Parameter(defaultValue = "30000")
  private final int shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;

  /**
   * The timeout in milliseconds after the Tests should be finished for sure. The environment will
   * be stopped when this exceeds. Default value is five minutes.
   */
  @Parameter(defaultValue = "180000")
  private final int timeout = DEFAULT_TEST_RUN_TIMEOUT;

  public List<EOSGiArtifact> getArtifacts() {
    return artifacts;
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

  public Integer getInitialBundleStartLevel() {
    return initialBundleStartLevel;
  }

  public LaunchConfig getLaunchConfig() {
    return launchConfig;
  }

  public Map<String, String> getRuntimePathRegexes() {
    return runtimePathRegexes;
  }

  public int getShutdownTimeout() {
    return shutdownTimeout;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setFramework(final String framework) {
    this.framework = framework;
  }

  public void setId(final String id) {
    this.id = id;
  }

}
