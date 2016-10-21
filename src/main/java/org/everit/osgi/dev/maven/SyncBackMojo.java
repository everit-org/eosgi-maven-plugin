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
package org.everit.osgi.dev.maven;

import java.io.File;
import java.io.FileFilter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.everit.osgi.dev.maven.configuration.EnvironmentConfiguration;
import org.everit.osgi.dev.maven.util.FileManager;

/**
 * Synchronizes back the configured directories.
 */
@Mojo(name = "sync-back", requiresProject = true)
public class SyncBackMojo extends AbstractEOSGiMojo {

  /**
   * Path to folder where the distribution will be generated. The content of this folder will be
   * overridden if the files with same name already exist.
   *
   */
  @Parameter(property = "eosgi.distFolder", defaultValue = "${project.build.directory}/eosgi/dist")
  protected String distFolder;

  /**
   * The directory where there may be additional files to create the distribution package
   * (optional).
   */
  @Parameter(property = "eosgi.sourceDistFolder", defaultValue = "${basedir}/src/dist/")
  protected String sourceDistFolder;

  /**
   * List of relative folder paths that should be synchronized back to the source distribution
   * folder when this goal is called.
   */
  @Parameter
  protected Map<String, String> syncBackFolders;

  private boolean cleanNonTouchedFilesInFolderRecurse(final File file,
      final Set<File> touchedFiles) {
    if (file.isDirectory()) {
      File[] folderContents = file.listFiles((FileFilter) TrueFileFilter.INSTANCE);
      boolean clean = true;
      for (File content : folderContents) {
        clean = cleanNonTouchedFilesInFolderRecurse(content, touchedFiles) && clean;
      }
      if (clean && !touchedFiles.contains(file)) {
        file.delete();
        return true;
      } else {
        return false;
      }
    } else {
      if (!touchedFiles.contains(file)) {
        file.delete();
        return true;
      } else {
        return false;
      }
    }
  }

  @Override
  protected void doExecute() throws MojoExecutionException, MojoFailureException {
    if (syncBackFolders == null || syncBackFolders.size() == 0) {
      getLog().info("No folders are configured to sync back.");
      return;
    }

    EnvironmentConfiguration[] environments = getEnvironmentsToProcess();
    if (environments.length != 1) {
      throw new MojoExecutionException(
          "Select exactly one environment to synchronize its folders back to the source"
              + " distribution folder! You can select an environment with the "
              + "'eosgi.environmentId' system property or 'environmentIdsToProcess' plugin"
              + " configuration.");
    }

    EnvironmentConfiguration environmentConfiguration = environments[0];

    File sourceDistFolderFile = new File(sourceDistFolder);
    File globalDistFolderFile = new File(distFolder);

    String environmentId = environmentConfiguration.getId();
    File environmentRootFolder = new File(globalDistFolderFile, environmentId);
    FileManager fileManager = new FileManager();
    for (Entry<String, String> syncBackFolder : syncBackFolders.entrySet()) {
      String relativeFolderPath = syncBackFolder.getValue();
      File envornmentDistSyncFolder = new File(environmentRootFolder, relativeFolderPath);
      File sourceSyncFolder = new File(sourceDistFolderFile, relativeFolderPath);
      sourceSyncFolder.mkdirs();
      getLog()
          .info("Syncing back '" + syncBackFolder.getKey() + "', from folder '"
              + envornmentDistSyncFolder + "' to folder '" + sourceSyncFolder + "'");
      fileManager.copyDirectory(envornmentDistSyncFolder, sourceSyncFolder);
      Set<File> touchedFiles = fileManager.getTouchedFiles();
      touchedFiles.add(sourceSyncFolder);
      cleanNonTouchedFilesInFolderRecurse(sourceSyncFolder, touchedFiles);
    }

  }

}
