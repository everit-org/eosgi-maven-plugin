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
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.plugin.MojoExecutionException;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.RuntimePathsType;

/**
 * Cleans the folder of an environment from unwanted files after an incremental upgrade.
 */
public final class EnvironmentCleaner {

  public static void cleanEnvironmentFolder(final DistributionPackageType distributionPackage,
      final File environmentRootDir, final FileManager fileManager) throws MojoExecutionException {
    new EnvironmentCleaner(distributionPackage, environmentRootDir, fileManager).clean();
  }

  private final String environmentId;

  private final Path environmentRootPath;

  private Pattern[] runtimePathPatterns;

  private final Set<File> touchedFiles;

  private EnvironmentCleaner(final DistributionPackageType distributionPackage,
      final File environmentDir, final FileManager fileManager) throws MojoExecutionException {

    this.environmentRootPath = environmentDir.toPath();
    this.environmentId = distributionPackage.getEnvironmentId();
    this.touchedFiles = fileManager.getTouchedFiles();

    RuntimePathsType runtimePaths = distributionPackage.getRuntimePaths();
    if (runtimePaths == null) {
      this.runtimePathPatterns = new Pattern[0];
    } else {
      List<String> pathRegexList = distributionPackage.getRuntimePaths().getPathRegex();
      this.runtimePathPatterns = new Pattern[pathRegexList.size()];
      int i = 0;
      for (String pathRegex : pathRegexList) {
        try {
          runtimePathPatterns[i] = Pattern.compile(pathRegex);
        } catch (PatternSyntaxException e) {
          throw new MojoExecutionException(
              "Invalid regular expression for runtime path in environment '" + environmentId + "': "
                  + pathRegex);
        }
        i++;
      }
    }
  }

  private void clean() {
    File environmentRootDir = environmentRootPath.toFile();
    cleanDirRecurse(environmentRootDir);
  }

  /**
   * Cleans the folder or file with sub-folders.
   *
   * @return True if the directory was deleted.
   */
  private boolean cleanDirRecurse(final File directory) {
    if (isPathRuntime(directory)) {
      return false;
    }
    File[] dirContents = directory.listFiles();
    boolean emptyAfterCleaning = true;
    for (File dirContent : dirContents) {
      if (dirContent.isDirectory()) {
        emptyAfterCleaning = emptyAfterCleaning && cleanDirRecurse(dirContent);
      } else {
        emptyAfterCleaning = emptyAfterCleaning && cleanFile(dirContent);
      }
    }
    if (emptyAfterCleaning && !touchedFiles.contains(directory)) {
      directory.delete();
      return true;
    } else {
      return false;
    }
  }

  private boolean cleanFile(final File file) {
    if (touchedFiles.contains(file) || isPathRuntime(file)) {
      return false;
    }

    file.delete();
    return true;
  }

  private boolean isPathRuntime(final File pathFile) {
    Path relativePath = environmentRootPath.relativize(pathFile.toPath());
    String relativePathString = relativePath.normalize().toString();
    for (Pattern runtimePathPatter : runtimePathPatterns) {
      if (runtimePathPatter.matcher(relativePathString).matches()) {
        return true;
      }
    }
    return false;
  }
}
