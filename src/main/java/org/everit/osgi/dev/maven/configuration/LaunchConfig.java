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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.everit.osgi.dev.eosgi.dist.schema.util.MergeUtil;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;

/**
 * The configuration of the launched OSGi Container.
 */
public class LaunchConfig extends AbstractLaunchConfig {

  /**
   * The overrides applied on this launch configuration.
   */
  @Parameter
  private LaunchConfigOverride[] overrides;

  private void checkForDuplicateOverrides(final LaunchConfigOverride[] launchConfigOverrides)
      throws MojoExecutionException {

    Set<UseByType> useBys = new HashSet<>();
    for (LaunchConfigOverride launchConfigOverride : launchConfigOverrides) {
      UseByType useBy = launchConfigOverride.useBy;
      if (useBys.contains(useBy)) {
        throw new MojoExecutionException("[" + useBy + "] defined twice in the configuration");
      }
      useBys.add(useBy);
    }
  }

  /**
   * Merges the <code>this</code> {@link LaunchConfig} to the {@link LaunchConfig} of the
   * environment and returns a new {@link LaunchConfig}.
   */
  public LaunchConfig createLaunchConfigForEnvironment(final String environmentId,
      final LaunchConfig environmentLaunchConfig, final String reportFolder,
      final Artifact jacocoAgentArtifact)
          throws MojoExecutionException {

    LaunchConfig rval = new LaunchConfig();

    rval.systemProperties = MergeUtil.mergeDefaults(
        systemProperties,
        environmentLaunchConfig.systemProperties);
    rval.vmArguments = MergeUtil.mergeDefaults(
        vmArguments,
        environmentLaunchConfig.vmArguments);
    rval.programArguments = MergeUtil.mergeDefaults(
        programArguments,
        environmentLaunchConfig.programArguments);

    Map<String, String> jacocoSettingsMap = MergeUtil.mergeDefaults(
        getJacocoSettingsMap(),
        environmentLaunchConfig.getJacocoSettingsMap());
    String jacocoAgentVmArgument = JacocoSettings.getJacocoAgentVmArgument(
        jacocoSettingsMap, environmentId, reportFolder, jacocoAgentArtifact);
    if (jacocoAgentVmArgument != null) {
      rval.vmArguments.put(JacocoSettings.VM_ARG_JACOCO, jacocoAgentVmArgument);
    }

    checkForDuplicateOverrides(overrides);
    checkForDuplicateOverrides(environmentLaunchConfig.overrides);

    Set<UseByType> processedUseBys = new HashSet<>();
    List<LaunchConfigOverride> rvalOverrides = new ArrayList<>();

    for (LaunchConfigOverride launchConfigOverride : overrides) {

      UseByType useBy = launchConfigOverride.useBy;
      LaunchConfigOverride other = getPair(useBy, environmentLaunchConfig.overrides);

      LaunchConfigOverride mergedLaunchConfigOverride = new LaunchConfigOverride();
      mergedLaunchConfigOverride.useBy = useBy;

      mergedLaunchConfigOverride.systemProperties = MergeUtil.mergeOverrides(
          launchConfigOverride.systemProperties,
          environmentLaunchConfig.systemProperties,
          other.systemProperties);
      mergedLaunchConfigOverride.vmArguments = MergeUtil.mergeOverrides(
          launchConfigOverride.vmArguments,
          environmentLaunchConfig.vmArguments,
          other.vmArguments);
      mergedLaunchConfigOverride.programArguments = MergeUtil.mergeOverrides(
          launchConfigOverride.programArguments,
          environmentLaunchConfig.programArguments,
          other.programArguments);

      jacocoSettingsMap = MergeUtil.mergeDefaults(
          launchConfigOverride.getJacocoSettingsMap(),
          other.getJacocoSettingsMap());
      jacocoAgentVmArgument = JacocoSettings.getJacocoAgentVmArgument(
          jacocoSettingsMap, environmentId, reportFolder, jacocoAgentArtifact);
      if (jacocoAgentVmArgument != null) {
        rval.vmArguments.put(JacocoSettings.VM_ARG_JACOCO,
            jacocoAgentVmArgument);
      }

      rvalOverrides.add(mergedLaunchConfigOverride);

      processedUseBys.add(useBy);
    }

    for (LaunchConfigOverride other : environmentLaunchConfig.overrides) {

      UseByType useBy = other.useBy;

      if (!processedUseBys.contains(useBy)) {

        LaunchConfigOverride mergedLaunchConfigOverride = new LaunchConfigOverride();
        mergedLaunchConfigOverride.useBy = useBy;

        mergedLaunchConfigOverride.systemProperties = MergeUtil.mergeOverrides(
            null,
            environmentLaunchConfig.systemProperties,
            other.systemProperties);
        mergedLaunchConfigOverride.vmArguments = MergeUtil.mergeOverrides(
            null,
            environmentLaunchConfig.vmArguments,
            other.vmArguments);
        mergedLaunchConfigOverride.programArguments = MergeUtil.mergeOverrides(
            null,
            environmentLaunchConfig.programArguments,
            other.programArguments);

        jacocoSettingsMap = MergeUtil.mergeDefaults(
            null,
            other.getJacocoSettingsMap());
        jacocoAgentVmArgument = JacocoSettings.getJacocoAgentVmArgument(
            jacocoSettingsMap, environmentId, reportFolder, jacocoAgentArtifact);
        if (jacocoAgentVmArgument != null) {
          rval.vmArguments.put(JacocoSettings.VM_ARG_JACOCO,
              jacocoAgentVmArgument);
        }

        rvalOverrides.add(mergedLaunchConfigOverride);

      }
    }

    rval.overrides = rvalOverrides.toArray(new LaunchConfigOverride[] {});

    return rval;
  }

  public LaunchConfigOverride[] getOverrides() {
    return overrides;
  }

  private LaunchConfigOverride getPair(final UseByType useBy,
      final LaunchConfigOverride[] environmentOverrides) {

    for (LaunchConfigOverride launchConfigOverride : environmentOverrides) {
      if (launchConfigOverride.useBy.equals(useBy)) {
        return launchConfigOverride;
      }
    }

    return null;
  }

  public void setOverrides(final LaunchConfigOverride[] overrides) {
    this.overrides = overrides;
  }

}
