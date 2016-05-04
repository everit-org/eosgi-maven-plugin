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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.UseByType;
import org.everit.osgi.dev.maven.util.AutoResolveArtifactHolder;
import org.junit.Assert;
import org.junit.Test;

public class LaunchConfigTest {

  private static final String ENVIRONMENT_ID = "test";

  private static final AutoResolveArtifactHolder JACOCO_AGENT_ARTIFACT =
      new AutoResolveArtifactHolder(new DefaultArtifact(
          "org.jacoco", "org.jacoco.agent", null, "0.7.5.201505241946")
              .setFile(new File("jacoco.jar") {

                private static final long serialVersionUID = 1L;

                @Override
                public String getAbsolutePath() {
                  return "jacoco.jar";
                };
              }),
          null);

  private static final String REPORT_FOLDER = "reportFolder";

  private void assertMap(final Map<String, String> actualMap,
      final String... expctedKeyValuePairs) {

    Map<String, String> expectedMap = convertKeyValuesPairsToMap(expctedKeyValuePairs);
    assertMapEquals(expectedMap, actualMap);
  }

  private void assertMapEquals(final Map<String, String> expected,
      final Map<String, String> actual) {

    Assert.assertNotNull(actual);

    Set<String> processedKeys = new HashSet<>();

    for (Entry<String, String> e : expected.entrySet()) {

      String expectedKey = e.getKey();
      String expectedValue = e.getValue();
      String actualValue = actual.get(expectedKey);

      if (expectedKey.equals(JacocoSettings.VM_ARG_JACOCO)) {
        Assert.assertEquals(
            cleanEnvironmentSpecValues(expectedValue),
            cleanEnvironmentSpecValues(actualValue));

      } else if (((expectedValue == null) && (actualValue != null))
          || ((expectedValue != null) && !expectedValue.equals(actualValue))) {
        Assert.fail("key value pair does not match: " + "expected key [" + expectedKey
            + "] value [" + expectedValue + "] actual value [" + actualValue + "]\n"
            + "expected map [" + expected + "]\n"
            + "actual map [" + actual + "]");
      }

      processedKeys.add(expectedKey);
    }

    Map<String, String> actualClone = new HashMap<>(actual);
    for (String processedKey : processedKeys) {
      actualClone.remove(processedKey);
    }

    Assert.assertTrue("More key value pairs than expected: " + actualClone + "\n"
        + "expected map [" + expected + "]\n"
        + "actual map [" + actual + "]",
        actualClone.isEmpty());
  }

  private String cleanEnvironmentSpecValues(final String jacocoAgent) {
    if (jacocoAgent == null) {
      return null;
    }
    return jacocoAgent.substring(0, jacocoAgent.indexOf("destfile"));
  }

  private Map<String, String> convertKeyValuesPairsToMap(final String... keyValuePairs) {

    if ((keyValuePairs.length % 2) != 0) {
      Assert.fail("the last key defined without value");
    }

    Map<String, String> rval = new HashMap<>();

    for (int i = 0; i < keyValuePairs.length; i = i + 2) {
      rval.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }

    return rval;
  }

  @Test
  public void testCreateLaunchConfigForEnvironment() throws MojoExecutionException {

    JacocoSettings defaultJacoco = new JacocoSettings(
        "defaultAddress", true, true, "defaultExcludes", "defaultIncludes", "defaultOutput", 1);
    JacocoSettings ideJacoco = null;
    JacocoSettings parsablesJacoco = new JacocoSettings(
        null, true, true, null, null, "parsablesOutput", null);

    Map<String, String> defaultPA = new HashMap<>();
    defaultPA.put("pa1", "pa1v1default");
    defaultPA.put("pa2", "pa2v1default");
    Map<String, String> idePA = null;
    Map<String, String> parsablesPA = new HashMap<>();
    parsablesPA.put("pa1", "");
    parsablesPA.put("pa2", "pa2v2parsables");

    Map<String, String> parsablesSP = new HashMap<>();
    parsablesSP.put("sp1", "sp1v1parsables");
    parsablesSP.put("sp2", "");
    parsablesSP.put("sp3", "sp3v2parsables");

    Map<String, String> defaultVMA = null;
    Map<String, String> ideVMA = new HashMap<>();
    ideVMA.put("vma1", "vma1v1ide");
    Map<String, String> parsablesVMA = new HashMap<>();

    LaunchConfigOverride[] defaultOverrides = new LaunchConfigOverride[] {
        new LaunchConfigOverride(UseByType.IDE,
            ideJacoco, idePA, ideVMA),
        new LaunchConfigOverride(UseByType.PARSABLES,
            parsablesJacoco, parsablesPA, parsablesVMA),
    };

    LaunchConfig launchConfig =
        new LaunchConfig(defaultJacoco, defaultPA, defaultVMA, defaultOverrides);

    JacocoSettings envDefaultJacoco = null;
    JacocoSettings envIdeJacoco = new JacocoSettings(
        "envIdeAddress", false, false, null, "envIdeIncludes", "envIdeOutput", null);

    Map<String, String> envDefaultPA = new HashMap<>();
    envDefaultPA.put("pa3", "pa3v1default");
    envDefaultPA.put("pa4", "pa4v1default");
    Map<String, String> envIdePA = new HashMap<>();
    envIdePA.put("pa4", null);

    Map<String, String> envDefaultVMA = null;
    Map<String, String> envIdeVMA = new HashMap<>();
    envIdeVMA.put("vma2", "vma2v1ide");

    LaunchConfigOverride[] envDefaultOverrides = new LaunchConfigOverride[] {
        new LaunchConfigOverride(UseByType.IDE,
            envIdeJacoco, envIdePA, envIdeVMA),
    };

    LaunchConfig environmentLaunchConfig = new LaunchConfig(
        envDefaultJacoco, envDefaultPA, envDefaultVMA, envDefaultOverrides);

    LaunchConfig deDefaultLC = launchConfig.createLaunchConfigForEnvironment(
        environmentLaunchConfig, ENVIRONMENT_ID, REPORT_FOLDER, JACOCO_AGENT_ARTIFACT);

    Assert.assertNotNull(deDefaultLC);

    assertMap(deDefaultLC.getProgramArguments(),
        "pa1", "pa1v1default",
        "pa2", "pa2v1default",
        "pa3", "pa3v1default",
        "pa4", "pa4v1default");
    assertMap(deDefaultLC.getVmArguments(),
        JacocoSettings.VM_ARG_JACOCO, "-javaagent:jacoco.jar="
            + "append=true,dumponexit=true,"
            + "includes=defaultIncludes,excludes=defaultExcludes,"
            + "output=defaultOutput,address=defaultAddress,port=1,"
            + "destfile=IGNORED,sessionid=IGNORED");
    Assert.assertNotNull(deDefaultLC.getOverrides());

    Map<UseByType, LaunchConfigOverride> deDefaultLCOs = new HashMap<>();
    for (LaunchConfigOverride deDefaultLCO : deDefaultLC.getOverrides()) {
      deDefaultLCOs.put(deDefaultLCO.getUseBy(), deDefaultLCO);
    }

    LaunchConfigOverride deITLCO = deDefaultLCOs.get(UseByType.INTEGRATION_TEST);
    Assert.assertNull(deITLCO);

    LaunchConfigOverride deIdeLCO = deDefaultLCOs.get(UseByType.IDE);
    Assert.assertNotNull(deIdeLCO);

    Assert.assertEquals(UseByType.IDE, deIdeLCO.getUseBy());
    assertMap(deIdeLCO.getProgramArguments(),
        "pa4", null);
    assertMap(deIdeLCO.getVmArguments(),
        "vma1", "vma1v1ide",
        "vma2", "vma2v1ide",
        JacocoSettings.VM_ARG_JACOCO, "-javaagent:jacoco.jar="
            + "append=false,dumponexit=false,"
            + "includes=envIdeIncludes,"
            + "output=envIdeOutput,address=envIdeAddress,"
            + "destfile=IGNORED,sessionid=IGNORED");

    LaunchConfigOverride deParsablesLCO = deDefaultLCOs.get(UseByType.PARSABLES);
    Assert.assertNotNull(deParsablesLCO);

    Assert.assertEquals(UseByType.PARSABLES, deParsablesLCO.getUseBy());
    assertMap(deParsablesLCO.getProgramArguments(),
        "pa1", "",
        "pa2", "pa2v2parsables");
    assertMap(deParsablesLCO.getVmArguments(),
        JacocoSettings.VM_ARG_JACOCO, "-javaagent:jacoco.jar="
            + "append=true,dumponexit=true,"
            + "output=parsablesOutput,"
            + "destfile=IGNORED,sessionid=IGNORED");
  }

  @Test
  public void testCreateLaunchConfigForEnvironmentWithNullValues() throws MojoExecutionException {
    LaunchConfig launchConfig = new LaunchConfig();
    LaunchConfig environmentLaunchConfig = new LaunchConfig();
    LaunchConfig deDefaultLC = launchConfig.createLaunchConfigForEnvironment(
        environmentLaunchConfig, ENVIRONMENT_ID, REPORT_FOLDER, JACOCO_AGENT_ARTIFACT);

    Assert.assertNotNull(deDefaultLC);

    assertMap(deDefaultLC.getProgramArguments());
    assertMap(deDefaultLC.getVmArguments());

    Assert.assertNotNull(deDefaultLC.getOverrides());
  }

}
