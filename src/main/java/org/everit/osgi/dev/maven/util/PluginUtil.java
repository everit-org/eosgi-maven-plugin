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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.aether.artifact.Artifact;

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

  /**
   * Creates the absolute file of an artifact where it is placed in the environment folder.
   *
   * @param artifact
   *          The artifact.
   * @param mavenArtifact
   *          The resolved maven artifact.
   * @param environmentRootFolder
   *          The root folder of the distributed environment.
   * @return The absolute file of the artifact in the distributed environment.
   */
  public static File resolveArtifactAbsoluteFile(final DistributableArtifact artifact,
      final Artifact mavenArtifact, final File environmentRootFolder) {

    File artifactRelativeFile = resolveArtifactRelativeFile(artifact, mavenArtifact);
    File absoluteArtifactFile = new File(environmentRootFolder, artifactRelativeFile.getPath());
    return absoluteArtifactFile;
  }

  private static File resolveArtifactRelativeFile(final DistributableArtifact artifact,
      final Artifact mavenArtifact) {
    String targetFile = artifact.targetFile;
    if (targetFile == null) {
      targetFile = mavenArtifact.getFile().getName();
    }

    String targetFolder = artifact.targetFolder;
    if (targetFolder != null) {
      File targetFolderFile = new File(targetFolder);
      return new File(targetFolderFile, targetFile);
    } else {
      return new File(targetFile);
    }
  }

  private PluginUtil() {
  }
}
