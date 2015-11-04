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
package org.everit.osgi.dev.maven.statistic;

/**
 * Utility class to System Environment.
 */
public final class EnvironmentUtil {

  private static final String ENV_BAMBOO_PLAN_KEY = "bamboo_planKey";

  private static final String ENV_BUILD_NUMBER = "BUILD_NUMBER";

  private static final String ENV_BUILDKITE = "BUILDKITE";

  private static final String ENV_CI = "CI";

  private static final String ENV_CONTINUOUS_INTEGRATION = "CONTINUOUS_INTEGRATION";

  private static final String ENV_HUDSON_URL = "HUDSON_URL";

  private static final String ENV_JENKINS_URL = "JENKINS_URL";

  private static final String ENV_RUN_ID = "RUN_ID";

  private static final String ENV_TASK_ID = "TASK_ID";

  private static final String ENV_TEAMCITY_VERSION = "TEAMCITY_VERSION";

  private static final String ENV_TF_BUILD = "TF_BUILD";

  private static boolean isBambooEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_BAMBOO_PLAN_KEY);
  }

  private static boolean isBuildkiteEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_BUILDKITE);
  }

  /**
   * Returns <code>true</code> if the current environment is a Continuous Integration server.
   */
  public static boolean isCi() {
    return EnvironmentUtil.isGenericCiEnvironment() || EnvironmentUtil.isSpecificCiEnvironment();
  }

  private static boolean isDefinedEnvironmentVariable(final String variableName) {
    return System.getenv(variableName) != null;
  }

  private static boolean isGenericCiEnvironment() {
    // Travis CI, CircleCI, GitlabCI,Appveyor, CodeShip, Jenkins, TeamCity, ...
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_CI)
        || EnvironmentUtil.isDefinedEnvironmentVariable(ENV_CONTINUOUS_INTEGRATION)
        || EnvironmentUtil.isDefinedEnvironmentVariable(ENV_BUILD_NUMBER);
  }

  private static boolean isHudsonEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_HUDSON_URL);
  }

  private static boolean isJenkinsEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_JENKINS_URL);
  }

  private static boolean isSpecificCiEnvironment() {
    return EnvironmentUtil.isJenkinsEnvironment()
        || EnvironmentUtil.isBambooEnvironment()
        || EnvironmentUtil.isTeamFoundationServerEnvironment()
        || EnvironmentUtil.isTeamCityEnvironment()
        || EnvironmentUtil.isBuildkiteEnvironment()
        || EnvironmentUtil.isHudsonEnvironment()
        || EnvironmentUtil.isTaskClusterEnvironment();
  }

  private static boolean isTaskClusterEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_TASK_ID)
        && EnvironmentUtil.isDefinedEnvironmentVariable(ENV_RUN_ID);
  }

  private static boolean isTeamCityEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_TEAMCITY_VERSION);
  }

  private static boolean isTeamFoundationServerEnvironment() {
    return EnvironmentUtil.isDefinedEnvironmentVariable(ENV_TF_BUILD);
  }

  private EnvironmentUtil() {
  }

}
