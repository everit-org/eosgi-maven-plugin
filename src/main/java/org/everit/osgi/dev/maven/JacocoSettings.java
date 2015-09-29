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

import org.apache.maven.plugins.annotations.Parameter;

/**
 * Settings for jacoco. For more information, please see
 * {@link http://www.eclemma.org/jacoco/trunk/doc/agent.html}.
 *
 */
public class JacocoSettings {

  @Parameter
  private String address;

  @Parameter
  private boolean append = true;

  @Parameter
  private boolean dumponexit = true;

  @Parameter
  private String excludes;

  @Parameter
  private String includes;

  @Parameter
  private String output;

  @Parameter
  private Integer port;

  public String getAddress() {
    return address;
  }

  public String getExcludes() {
    return excludes;
  }

  public String getIncludes() {
    return includes;
  }

  public String getOutput() {
    return output;
  }

  public Integer getPort() {
    return port;
  }

  public boolean isAppend() {
    return append;
  }

  public boolean isDumponexit() {
    return dumponexit;
  }

  public void setAddress(final String address) {
    this.address = address;
  }

  public void setAppend(final boolean append) {
    this.append = append;
  }

  public void setDumponexit(final boolean dumponexit) {
    this.dumponexit = dumponexit;
  }

  public void setExcludes(final String excludes) {
    this.excludes = excludes;
  }

  public void setIncludes(final String includes) {
    this.includes = includes;
  }

  public void setOutput(final String output) {
    this.output = output;
  }

  public void setPort(final Integer port) {
    this.port = port;
  }

  @Override
  public String toString() {
    return "JacocoSettings [append=" + append + ", includes=" + includes + ", excludes=" + excludes
        + ", dumponexit=" + dumponexit + "]";
  }

}
