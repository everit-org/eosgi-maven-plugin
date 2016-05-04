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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.everit.osgi.dev.eosgi.dist.schema.util.MergeUtil;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;
import org.everit.osgi.dev.maven.util.AutoResolveArtifactHolder;

/**
 * The configuration of the launched OSGi Container.
 */
public class LaunchConfig extends AbstractLaunchConfig {

  /**
   * The overrides applied on this launch configuration.
   */
  @Parameter
  private LaunchConfigOverride[] overrides;

  public LaunchConfig() {
    super();
  }

  LaunchConfig(final JacocoSettings jacoco, final Map<String, String> programArguments,
      final Map<String, String> vmArguments,
      final LaunchConfigOverride[] overrides) {
    super(jacoco, programArguments, vmArguments);
    this.overrides = overrides;
  }

  private void checkForDuplicateOverrides(final LaunchConfigOverride[] launchConfigOverrides)
      throws MojoExecutionException {

    if (launchConfigOverrides == null) {
      return;
    }

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
  public LaunchConfig createLaunchConfigForEnvironment(final LaunchConfig environmentLaunchConfig,
      final String environmentId, final String reportFolder,
      final AutoResolveArtifactHolder jacocoAgentArtifact)
      throws MojoExecutionException {

    LaunchConfig rval = new LaunchConfig();

    mergeDefaults(rval, environmentLaunchConfig,
        environmentId, reportFolder, jacocoAgentArtifact);

    checkForDuplicateOverrides(
        overrides);
    checkForDuplicateOverrides(
        environmentLaunchConfig == null ? null : environmentLaunchConfig.overrides);

    Set<UseByType> processedUseBys = new HashSet<>();
    List<LaunchConfigOverride> rvalOverrides = new ArrayList<>();

    if (overrides != null) {
      for (LaunchConfigOverride o1 : overrides) {

        UseByType useBy = o1.useBy;

        LaunchConfigOverride o2 = null;
        if (environmentLaunchConfig != null) {
          o2 = getPairOfUseBy(useBy, environmentLaunchConfig.overrides);
        }

        LaunchConfigOverride mergedLaunchConfigOverride =
            createMergedLaunchConfigOverride(o1, environmentLaunchConfig, o2,
                environmentId, reportFolder, jacocoAgentArtifact, useBy);

        rvalOverrides.add(mergedLaunchConfigOverride);

        processedUseBys.add(useBy);
      }
    }

    if ((environmentLaunchConfig != null) && (environmentLaunchConfig.overrides != null)) {

      for (LaunchConfigOverride o2 : environmentLaunchConfig.overrides) {

        UseByType useBy = o2.useBy;

        if (!processedUseBys.contains(useBy)) {

          LaunchConfigOverride mergedLaunchConfigOverride =
              createMergedLaunchConfigOverride(null, environmentLaunchConfig,
                  o2, environmentId, reportFolder, jacocoAgentArtifact, useBy);

          rvalOverrides.add(mergedLaunchConfigOverride);

        }
      }
    }

    rval.overrides = rvalOverrides.toArray(new LaunchConfigOverride[] {});

    return rval;
  }

  private LaunchConfigOverride createMergedLaunchConfigOverride(
      final LaunchConfigOverride o1,
      final LaunchConfig d2,
      final LaunchConfigOverride o2,
      final String environmentId, final String reportFolder,
      final AutoResolveArtifactHolder jacocoAgentArtifact,
      final UseByType useBy) throws MojoExecutionException {

    LaunchConfigOverride mergedLaunchConfigOverride = new LaunchConfigOverride();
    mergedLaunchConfigOverride.useBy = useBy;

    mergedLaunchConfigOverride.vmArguments = MergeUtil.mergeOverrides(
        getVmArgumentsIfAvailable(o1),
        getVmArgumentsIfAvailable(d2),
        getVmArgumentsIfAvailable(o2));
    mergedLaunchConfigOverride.programArguments = MergeUtil.mergeOverrides(
        getProgramArgumentsIfAvailable(o1),
        getProgramArgumentsIfAvailable(d2),
        getProgramArgumentsIfAvailable(o2));

    Map<String, String> jacocoSettingsMap = MergeUtil.mergeDefaults(
        o1 == null ? null : o1.getJacocoSettingsMap(),
        o2 == null ? null : o2.getJacocoSettingsMap());

    String jacocoAgentVmArgument = JacocoSettings.getJacocoAgentVmArgument(
        jacocoSettingsMap, environmentId, reportFolder, jacocoAgentArtifact);
    if (jacocoAgentVmArgument != null) {
      mergedLaunchConfigOverride.vmArguments.put(
          JacocoSettings.VM_ARG_JACOCO, jacocoAgentVmArgument);
    }

    return mergedLaunchConfigOverride;
  }

  public LaunchConfigOverride[] getOverrides() {
    return overrides;
  }

  private LaunchConfigOverride getPairOfUseBy(final UseByType useBy,
      final LaunchConfigOverride[] environmentOverrides) {

    for (LaunchConfigOverride launchConfigOverride : environmentOverrides) {
      if (launchConfigOverride.useBy.equals(useBy)) {
        return launchConfigOverride;
      }
    }

    return null;
  }

  private Map<String, String> getProgramArgumentsIfAvailable(
      final AbstractLaunchConfig abstractLaunchConfig) {
    if (abstractLaunchConfig == null) {
      return null;
    }
    return abstractLaunchConfig.programArguments;
  }

  private Map<String, String> getVmArgumentsIfAvailable(
      final AbstractLaunchConfig abstractLaunchConfig) {
    if (abstractLaunchConfig == null) {
      return null;
    }
    return abstractLaunchConfig.vmArguments;
  }

  private void mergeDefaults(final LaunchConfig rval, final LaunchConfig environmentLaunchConfig,
      final String environmentId, final String reportFolder,
      final AutoResolveArtifactHolder jacocoAgentArtifact) throws MojoExecutionException {

    rval.vmArguments = MergeUtil.mergeDefaults(
        vmArguments,
        environmentLaunchConfig == null ? null : environmentLaunchConfig.vmArguments);
    rval.programArguments = MergeUtil.mergeDefaults(
        programArguments,
        environmentLaunchConfig == null ? null : environmentLaunchConfig.programArguments);

    Map<String, String> jacocoSettingsMap = MergeUtil.mergeDefaults(
        getJacocoSettingsMap(),
        environmentLaunchConfig == null ? null : environmentLaunchConfig.getJacocoSettingsMap());

    String jacocoAgentVmArgument = JacocoSettings.getJacocoAgentVmArgument(
        jacocoSettingsMap, environmentId, reportFolder, jacocoAgentArtifact);
    if (jacocoAgentVmArgument != null) {
      rval.vmArguments.put(
          JacocoSettings.VM_ARG_JACOCO, jacocoAgentVmArgument);
    }

  }

}
