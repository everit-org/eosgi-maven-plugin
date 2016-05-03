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

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;

/**
 * Settings for jacoco. For more information, please see
 * {@link http://www.eclemma.org/jacoco/trunk/doc/agent.html}.
 */
public class JacocoSettings {

  private static final String ADDRESS = "address";

  private static final String APPEND = "append";

  private static final String DUMP_ON_EXIT = "dumponexit";

  private static final String EXCLUDES = "excludes";

  private static final String INCLUDES = "includes";

  private static final String OUTPUT = "output";

  private static final String PORT = "port";

  public static final String VM_ARG_JACOCO = "javaagentJacoco";

  /**
   * Returns the environment specific VM argument of the Jacoco java agent or <code>null</code> if
   * the provided settings is <code>null</code>. This method also creates the report folder
   * belonging to the environment.
   */
  public static String getJacocoAgentVmArgument(final Map<String, String> jacocoSettingsMap,
      final String environmentId, final String reportFolder,
      final Artifact jacocoAgentArtifact) {

    if ((jacocoSettingsMap == null) || jacocoSettingsMap.isEmpty()) {
      return null;
    }

    JacocoSettings merged = JacocoSettings.valueOf(jacocoSettingsMap);

    File jacocoAgentFile = jacocoAgentArtifact.getFile();
    String jacocoAgentAbsPath = jacocoAgentFile.getAbsolutePath();

    StringBuilder sb = new StringBuilder("-javaagent:");
    sb.append(jacocoAgentAbsPath);
    sb.append("=append=").append(Boolean.valueOf(merged.append).toString());
    sb.append(",dumponexit=").append(Boolean.valueOf(merged.dumponexit).toString());
    if (merged.includes != null) {
      sb.append(",includes=").append(merged.includes);
    }
    if (merged.excludes != null) {
      sb.append(",excludes=").append(merged.excludes);
    }

    if (merged.output != null) {
      sb.append(",output=").append(merged.output);

    }
    if (merged.address != null) {
      sb.append(",address=").append(merged.address);
    }
    if (merged.port != null) {
      sb.append(",port=").append(merged.port);
    }

    File reportFolderFile = new File(reportFolder, environmentId);
    reportFolderFile.mkdirs();
    File jacocoExecFile = new File(reportFolderFile, "jacoco.exec");

    sb.append(",destfile=").append(jacocoExecFile.getAbsolutePath());
    sb.append(",sessionid=").append(environmentId).append("_").append(new Date().getTime());

    return sb.toString();
  }

  /**
   * Converts the key-values pairs map to a {@link JacocoSettings} instance.
   */
  private static JacocoSettings valueOf(final Map<String, String> jacocoSettingsMap) {
    JacocoSettings rval = new JacocoSettings();
    rval.address = jacocoSettingsMap.get(ADDRESS);
    rval.append = Boolean.valueOf(jacocoSettingsMap.get(APPEND));
    rval.dumponexit = Boolean.valueOf(jacocoSettingsMap.get(DUMP_ON_EXIT));
    rval.excludes = jacocoSettingsMap.get(EXCLUDES);
    rval.includes = jacocoSettingsMap.get(INCLUDES);
    rval.output = jacocoSettingsMap.get(OUTPUT);
    String stringPort = jacocoSettingsMap.get(PORT);
    rval.port =
        ((stringPort == null) || "null".equals(stringPort)) ? null : Integer.valueOf(stringPort);
    return rval;
  }

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

  /**
   * Default constructor.
   */
  public JacocoSettings() {
  }

  /**
   * Constructor.
   */
  JacocoSettings(final String address, final boolean append, final boolean dumponexit,
      final String excludes, final String includes, final String output, final Integer port) {
    super();
    this.address = address;
    this.append = append;
    this.dumponexit = dumponexit;
    this.excludes = excludes;
    this.includes = includes;
    this.output = output;
    this.port = port;
  }

  /**
   * Converts the dedicated member variables to a map as key-value pairs.
   */
  public Map<String, String> toMap() {
    Map<String, String> rval = new HashMap<>();
    rval.put(ADDRESS, address);
    rval.put(APPEND, String.valueOf(append));
    rval.put(DUMP_ON_EXIT, String.valueOf(dumponexit));
    rval.put(EXCLUDES, excludes);
    rval.put(INCLUDES, includes);
    rval.put(OUTPUT, output);
    rval.put(PORT, String.valueOf(port));
    return rval;
  }
}
