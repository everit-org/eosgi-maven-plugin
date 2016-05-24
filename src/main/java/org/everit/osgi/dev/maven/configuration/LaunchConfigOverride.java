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

import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;
import org.everit.osgi.dev.dist.util.configuration.schema.UseByType;

/**
 * The configuration of the launched OSGi Container that can override other configurations.
 */
public class LaunchConfigOverride extends AbstractLaunchConfig {

  /**
   * Defines the case where these overrides must be used.
   */
  @Parameter(required = true)
  protected UseByType useBy;

  public LaunchConfigOverride() {
    super();
  }

  LaunchConfigOverride(final UseByType useBy, final JacocoSettings jacoco,
      final Map<String, String> programArguments, final Map<String, String> vmArguments) {
    super(jacoco, programArguments, vmArguments);
    this.useBy = useBy;
  }

  public UseByType getUseBy() {
    return useBy;
  }

}
