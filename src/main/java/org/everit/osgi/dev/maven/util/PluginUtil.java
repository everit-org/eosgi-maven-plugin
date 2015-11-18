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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.maven.plugin.logging.Log;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;

/**
 * Util functions for every plugin in this library.
 */
public final class PluginUtil {

  public static final String OS_LINUX_UNIX = "linux";

  public static final String OS_MACINTOSH = "mac";

  public static final String OS_SUNOS = "sunos";

  public static final String OS_WINDOWS = "windows";

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
  public static Map<ArtifactKey, ArtifactType> createArtifactMap(
      final DistributionPackageType distributionPackage) {
    if (distributionPackage == null) {
      return Collections.emptyMap();
    }
    ArtifactsType artifacts = distributionPackage.getArtifacts();
    if (artifacts == null) {
      return Collections.emptyMap();
    }
    Map<ArtifactKey, ArtifactType> result = new HashMap<>();
    List<ArtifactType> artifactList = artifacts.getArtifact();
    for (ArtifactType artifact : artifactList) {
      ArtifactKey artifactKey = new ArtifactKey(artifact);
      if (result.containsKey(artifactKey)) {
        throw new DuplicateArtifactException(artifactKey);
      }
      result.put(artifactKey, artifact);
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
      final Map<ArtifactKey, ArtifactType> currentArtifactMap,
      final ArtifactsType artifacts) {
    Map<ArtifactKey, ArtifactType> tmpArtifactMap = new HashMap<>(currentArtifactMap);
    if (artifacts == null) {
      return new ArrayList<>(currentArtifactMap.values());
    }
    List<ArtifactType> artifactList = artifacts.getArtifact();
    for (ArtifactType artifact : artifactList) {
      ArtifactKey artifactKey = new ArtifactKey(artifact);
      tmpArtifactMap.remove(artifactKey);
    }
    return new ArrayList<>(tmpArtifactMap.values());
  }

  /**
   * Returns the java command to execute other java processes.
   */
  public static String getJavaCommand() {
    String javaHome = System.getProperty("java.home");
    String os = PluginUtil.getOS();
    String extension = OS_WINDOWS.equals(os) ? ".exe" : ".sh";
    return javaHome + "/bin/java" + extension;
  }

  /**
   * Returns the OS type.
   *
   * @return The operating system type.
   */
  public static String getOS() {
    String os = System.getProperty("os.name").toLowerCase(Locale.getDefault());
    if (os.indexOf("win") >= 0) {
      return OS_WINDOWS;
    }
    if (os.indexOf("mac") >= 0) {
      return OS_MACINTOSH;
    }
    if (((os.indexOf("nix") >= 0) || (os.indexOf("nux") >= 0))) {
      return OS_LINUX_UNIX;
    }
    if (os.indexOf("sunos") >= 0) {
      return OS_SUNOS;
    }
    return null;
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
   * Sends a command to a socket and returns a response. This function works based on line breaks.
   *
   * @param command
   *          The command to send.
   * @param socket
   *          The socket to send the command to.
   * @param serverName
   *          The name of the server.
   * @param log
   *          The logger where debug information will be written.
   * @return The response from the server.
   * @throws IOException
   *           if there is a problem in the connection.
   */
  public static String sendCommandToSocket(final String command, final Socket socket,
      final String serverName,
      final Log log)
          throws IOException {
    log.debug("Sending command to " + serverName + ": " + command);
    InputStream inputStream = socket.getInputStream();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()));
    OutputStream outputStream = socket.getOutputStream();
    outputStream.write((command + "\n").getBytes(Charset.defaultCharset()));
    outputStream.flush();
    String response = reader.readLine();
    log.debug("Got response from " + serverName + ": " + response);
    return response;
  }

  private PluginUtil() {
  }
}
