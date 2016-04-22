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
package org.everit.osgi.dev.maven.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoFailureException;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;

/**
 * Util functions for every plugin in this library.
 */
public final class PluginUtil {

  /**
   * Converts a Map to a list where the entry of the list contains an array with the key and the
   * value.
   *
   * @param map
   *          The map that will be converted.
   * @return A list of String arrays where the first element of the array is the key and the second
   *         is the value.
   */
  public static List<String[]> convertMapToList(final Map<String, String> map) {
    List<String[]> result = new ArrayList<>();
    for (Entry<String, String> entry : map.entrySet()) {
      String[] newEntry = new String[] { entry.getKey(), entry.getValue() };
      result.add(newEntry);
    }
    return result;
  }

  /**
   * Creates the artifact map for the distribution package.
   *
   * @param distributionPackage
   *          The distribution package.
   * @return The artifact map.
   */
  public static Map<String, ArtifactType> createArtifactMap(
      final DistributionPackageType distributionPackage) {

    if (distributionPackage == null) {
      return Collections.emptyMap();
    }

    ArtifactsType artifacts = distributionPackage.getArtifacts();
    if (artifacts == null) {
      return Collections.emptyMap();
    }

    Map<String, ArtifactType> result = new HashMap<>();
    List<ArtifactType> artifactList = artifacts.getArtifact();

    for (ArtifactType artifact : artifactList) {

      BundleDataType bundleDataType = artifact.getBundle();
      if (bundleDataType != null) {

        String location = bundleDataType.getLocation();
        if (result.containsKey(location)) {
          throw new DuplicateArtifactException(location);
        }
        result.put(location, artifact);
      }
    }

    return result;
  }

  /**
   * Deletes a folder with its content from the computer.
   *
   * @param folder
   *          The folder that should be deleted.
   */
  public static void deleteFolderRecurse(final File folder) {
    if (folder.exists()) {
      File[] subFiles = folder.listFiles();
      for (File subFile : subFiles) {
        if (subFile.isDirectory()) {
          PluginUtil.deleteFolderRecurse(subFile);
        } else {
          subFile.delete();
        }
      }
      folder.delete();
    }
  }

  /**
   * Calculates the artifacts that should be deleted after an upgrade.
   *
   * @param currentArtifactMap
   *          The artifact map that is currently installed.
   * @param artifacts
   *          The artifacts of the new distribution package that will be installed.
   * @return The artifact list that should be deleted.
   */
  public static List<ArtifactType> getArtifactsToRemove(
      final Map<String, ArtifactType> currentArtifactMap,
      final ArtifactsType artifacts) {

    Map<String, ArtifactType> tmpArtifactMap = new HashMap<>(currentArtifactMap);
    if (artifacts == null) {
      return new ArrayList<>(currentArtifactMap.values());
    }

    List<ArtifactType> artifactList = artifacts.getArtifact();

    for (ArtifactType artifact : artifactList) {

      BundleDataType bundleDataType = artifact.getBundle();

      if (bundleDataType != null) {
        String location = bundleDataType.getLocation();
        tmpArtifactMap.remove(location);
      }
    }

    return new ArrayList<>(tmpArtifactMap.values());
  }

  /**
   * Returns the java command to execute other java processes using the same Java as the current
   * process.
   */
  public static String getJavaCommand() throws MojoFailureException {
    String javaHome = System.getProperty("java.home");
    return javaHome + "/bin/java";
  }

  /**
   * Checks whether the content of the two buffers are the same.
   *
   * @param original
   *          The original buffer.
   * @param originalLength
   *          The length of the original buffer that should be checked.
   * @param target
   *          The target buffer that should be the same.
   * @return Whether the two buffers are the same or not.
   */
  public static boolean isBufferSame(final byte[] original, final int originalLength,
      final byte[] target) {
    if (originalLength != target.length) {
      return false;
    }
    int i = 0;
    boolean same = true;
    while ((i < originalLength) && same) {
      same = original[i] == target[i];
      i++;
    }
    return same;
  }

  /**
   * Getting the normalized version of an artifact. The artifact has to have at least three digits
   * inside the version separated by dots. If there are less than two dots inside the version it is
   * extended with the necessary numbers of ".0".
   *
   * @param version
   *          The version that is checked.
   * @return A normalizad version.
   */
  public static String normalizeVersion(final String version) {
    int dotCount = 0;
    char[] versionCharArray = version.toCharArray();
    for (int i = 0, n = versionCharArray.length; (i < n) && (dotCount < 2); i++) {
      if (versionCharArray[i] == '.') {
        dotCount++;
      }
    }
    StringBuilder result = new StringBuilder(version);
    if (dotCount < 2) {
      result.append(".0");
    }
    if (dotCount < 1) {
      result.append(".0");
    }
    return result.toString();
  }

  private PluginUtil() {
  }
}
