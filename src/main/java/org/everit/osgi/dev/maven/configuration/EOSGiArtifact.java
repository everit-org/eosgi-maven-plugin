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
 * Dependency of the environment.
 */
public class EOSGiArtifact {

  @Parameter
  private String downloadURL;

  /**
   * The artifact coordinates in the format
   * &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]: &lt;version&gt;,
   * must not be null.
   */
  @Parameter(required = true)
  private String gav;

  @Parameter(defaultValue = "true")
  private boolean overrideProjectDependency;

  @Parameter
  private Map<String, String> properties = new HashMap<String, String>();

  @Parameter
  private String targetFile;

  @Parameter
  private String targetFolder;

  public String getDownloadURL() {
    return downloadURL;
  }

  public String getGav() {
    return gav;
  }

  public Map<String, String> getProperties() {
    return properties;
  }

  public String getTargetFile() {
    return targetFile;
  }

  public String getTargetFolder() {
    return targetFolder;
  }

  public boolean isOverrideProjectDependency() {
    return overrideProjectDependency;
  }

  public void setDownloadURL(final String downloadURL) {
    this.downloadURL = downloadURL;
  }

  public void setGav(final String gav) {
    this.gav = gav;
  }

  public void setOverrideProjectDependency(final boolean overrideProjectDependency) {
    this.overrideProjectDependency = overrideProjectDependency;
  }

  public void setProperties(final Map<String, String> properties) {
    this.properties = properties;
  }

  public void setTargetFile(final String targetFile) {
    this.targetFile = targetFile;
  }

  public void setTargetFolder(final String targetFolder) {
    this.targetFolder = targetFolder;
  }
}
