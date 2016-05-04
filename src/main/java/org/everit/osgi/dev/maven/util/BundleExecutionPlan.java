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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.EnvironmentType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.OSGiActionType;

/**
 * Creates a bundle execution plan on the OSGi container based on the old and new settings and the
 * content of the bundle files.
 */
public class BundleExecutionPlan {

  public final Collection<ArtifactType> installBundles;

  public final int lowestStartLevel;

  public final Collection<ArtifactType> removeBundles;

  public final Collection<ArtifactType> updateBundles;

  /**
   * Constructor.
   *
   * @param existingDistributedEnvironment
   *          The distribution that was previously generated.
   * @param newArtifacts
   *          The artifact list of the new distribution process.
   * @param environmentRootFolder
   *          The root folder of the environment where the artifacts will be copied.
   * @param defaultBundleStartLevel
   *          The default start level of the bundles that are newly installed.
   * @param artifactResolver
   *          Resolves the artifacts.
   * @throws MojoExecutionException
   *           If an exception happens during generating the execution plan.
   */
  public BundleExecutionPlan(final EnvironmentType existingDistributedEnvironment,
      final ArtifactsType newArtifacts, final File environmentRootFolder,
      final int defaultBundleStartLevel,
      final PredefinedRepoArtifactResolver artifactResolver)
      throws MojoExecutionException {

    Map<String, ArtifactType> bundleByLocation =
        createExistingBundleByLocationMap(existingDistributedEnvironment);

    Map<String, ArtifactType> installBundleMap = new HashMap<>();
    List<ArtifactType> newBundlesThatExisted = new ArrayList<>();

    for (ArtifactType newArtifact : newArtifacts.getArtifact()) {
      BundleDataType bundleData = newArtifact.getBundle();
      if (bundleData != null && !OSGiActionType.NONE.equals(bundleData.getAction())) {
        ArtifactType existingBundle = bundleByLocation.remove(bundleData.getLocation());
        if (existingBundle == null) {
          if (installBundleMap.containsKey(bundleData.getLocation())) {
            throw new MojoExecutionException(
                "Bundle location '" + bundleData.getLocation() + "' exists twice in environment '"
                    + existingDistributedEnvironment.getId() + "'");
          }
          installBundleMap.put(bundleData.getLocation(), newArtifact);
        } else {
          newBundlesThatExisted.add(newArtifact);
        }
      }
    }

    this.removeBundles = bundleByLocation.values();
    this.installBundles = installBundleMap.values();
    this.updateBundles = selectBundlesWithChangedContent(newBundlesThatExisted,
        environmentRootFolder, artifactResolver);

    this.lowestStartLevel = resolveLowestStartLevel(defaultBundleStartLevel);
  }

  private boolean contentDifferent(final File newArtifactFile, final File oldArtifactFile)
      throws MojoExecutionException {
    if (newArtifactFile.lastModified() == oldArtifactFile.lastModified()) {
      return false;
    }
    try (InputStream newIn = new BufferedInputStream(new FileInputStream(newArtifactFile));
        InputStream oldIn = new BufferedInputStream(new FileInputStream(oldArtifactFile))) {

      int oldR = 0;
      int newR = 0;

      boolean changed = false;
      while (oldR < 0 && !changed) {
        oldR = oldIn.read();
        newR = newIn.read();

        if (oldR != newR) {
          changed = true;
        }
      }
      return changed;
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Error during diff check on files: " + oldArtifactFile + ", " + newArtifactFile);
    }

  }

  private Map<String, ArtifactType> createExistingBundleByLocationMap(
      final EnvironmentType existingDistributedEnvironment) {

    Map<String, ArtifactType> result = new HashMap<>();
    if (existingDistributedEnvironment == null
        || existingDistributedEnvironment.getArtifacts() == null) {
      return result;
    }

    for (ArtifactType artifact : existingDistributedEnvironment.getArtifacts().getArtifact()) {
      BundleDataType bundleData = artifact.getBundle();
      if (bundleData != null
          && !OSGiActionType.NONE.equals(bundleData.getAction())) {

        result.put(bundleData.getLocation(), artifact);
      }
    }
    return result;
  }

  private Artifact resolveArtifact(final PredefinedRepoArtifactResolver artifactResolver,
      final ArtifactType artifact) throws MojoExecutionException {
    String extension = artifact.getType();
    if (extension == null) {
      extension = "jar";
    }
    ArtifactRequest artifactRequest = new ArtifactRequest();
    Artifact aetherArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
        artifact.getClassifier(), extension, artifact.getVersion());
    artifactRequest.setArtifact(aetherArtifact);

    Artifact resolvedArtifact = artifactResolver.resolve(artifactRequest);
    return resolvedArtifact;
  }

  private int resolveLowestStartLevel(final Collection<ArtifactType> bundleArtifacts,
      final int pLowestStartLevel) {
    int tmpLowestStartLevel = pLowestStartLevel;
    for (ArtifactType bundleArtifact : bundleArtifacts) {
      Integer startLevel = bundleArtifact.getBundle().getStartLevel();
      if (startLevel != null && startLevel.compareTo(tmpLowestStartLevel) < 0) {
        tmpLowestStartLevel = startLevel;
      }
    }
    return tmpLowestStartLevel;
  }

  private int resolveLowestStartLevel(final int defaultBundleStartLevel) {
    int tmpLowestStartLevel = defaultBundleStartLevel;
    tmpLowestStartLevel = resolveLowestStartLevel(this.installBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.removeBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.updateBundles, tmpLowestStartLevel);
    return tmpLowestStartLevel;
  }

  private Collection<ArtifactType> selectBundlesWithChangedContent(
      final List<ArtifactType> newBundlesThatExisted, final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver) throws MojoExecutionException {

    List<ArtifactType> result = new ArrayList<>();
    for (ArtifactType artifact : newBundlesThatExisted) {
      Artifact resolvedArtifact = resolveArtifact(artifactResolver, artifact);
      File newArtifactFile = resolvedArtifact.getFile();
      File oldArtifactFile =
          PluginUtil.resolveArtifactAbsoluteFile(artifact, resolvedArtifact, environmentRootFolder);

      if (contentDifferent(newArtifactFile, oldArtifactFile)) {
        result.add(artifact);
      }
    }
    return result;
  }
}
