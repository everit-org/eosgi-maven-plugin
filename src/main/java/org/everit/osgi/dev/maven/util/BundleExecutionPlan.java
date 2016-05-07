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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
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

  /**
   * Data of bundle with the start level that is currently set with the existing configuration.
   */
  public static class BundleDataWithCurrentStartLevel {
    public BundleDataType bundleData;

    public int oldStartLevel;

    public BundleDataWithCurrentStartLevel(final BundleDataType bundleData,
        final int oldStartLevel) {
      this.bundleData = bundleData;
      this.oldStartLevel = oldStartLevel;
    }

  }

  public final Collection<BundleDataType> changeStartLevelIfInitialBundleStartLevelChangesOnBundles;

  public final Collection<BundleDataType> higherStartLevelOnBundles;

  public final Collection<BundleDataType> installBundles;

  public final Collection<BundleDataType> lowerStartLevelOnBundles;

  /**
   * The lowest start level that was assigned to any of the bundles that change state in the plan or
   * <code>null</code> if none of these bundles had a specific startLevel.
   */
  public final Integer lowestStartLevel;

  public final Collection<BundleDataWithCurrentStartLevel> setInitialStartLevelOnBundles;

  public final Collection<BundleDataType> setStartLevelFromInitialBundles;

  public final Collection<BundleDataType> startStoppedBundles;

  public final Collection<BundleDataType> stopStartedBundles;

  public final Collection<BundleDataType> uninstallBundles;

  public final Collection<BundleDataType> updateBundles;

  /**
   * Constructor.
   *
   * @param existingDistributedEnvironment
   *          The distribution that was previously generated.
   * @param newArtifacts
   *          The artifact list of the new distribution process.
   * @param environmentRootFolder
   *          The root folder of the environment where the artifacts will be copied.
   * @param artifactResolver
   *          Resolves the artifacts.
   * @param artifactHandlerManager
   *          Artifact resolver.
   * @throws MojoExecutionException
   *           If an exception happens during generating the execution plan.
   */
  public BundleExecutionPlan(final EnvironmentType existingDistributedEnvironment,
      final ArtifactsType newArtifacts, final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver,
      final ArtifactHandlerManager artifactHandlerManager)
      throws MojoExecutionException {

    Map<String, BundleDataType> bundleByLocation =
        createExistingBundleByLocationMap(existingDistributedEnvironment);

    Map<String, BundleDataType> installBundleMap = new HashMap<>();

    List<BundleDataType> tmpUpdateBundles = new ArrayList<>();
    List<BundleDataType> tmpStopStartedBundles = new ArrayList<>();
    List<BundleDataType> tmpStartStoppedBundles = new ArrayList<>();
    List<BundleDataType> tmpLowerStartLevelOnBundles = new ArrayList<>();
    List<BundleDataType> tmpHigherStartLevelOnBundles = new ArrayList<>();
    List<BundleDataWithCurrentStartLevel> tmpSetInitialStartLevelOnBundles = new ArrayList<>();
    List<BundleDataType> tmpSetStartLevelFromInitialBundles = new ArrayList<>();
    List<BundleDataType> tmpIfInitialChanges = new ArrayList<>();

    for (ArtifactType newArtifact : newArtifacts.getArtifact()) {
      BundleDataType bundleData = newArtifact.getBundle();
      if (bundleData != null && !OSGiActionType.NONE.equals(bundleData.getAction())) {
        BundleDataType existingBundle = bundleByLocation.remove(bundleData.getLocation());
        if (existingBundle == null) {
          if (installBundleMap.containsKey(bundleData.getLocation())) {
            throw new MojoExecutionException(
                "Bundle location '" + bundleData.getLocation() + "' exists twice in environment '"
                    + existingDistributedEnvironment.getId() + "'");
          }
          installBundleMap.put(bundleData.getLocation(), bundleData);
        } else {
          if (bundleContentChanged(newArtifact, environmentRootFolder, artifactResolver,
              artifactHandlerManager)) {
            tmpUpdateBundles.add(bundleData);
          } else {
            if (bundleBecameStarted(bundleData, existingBundle)) {
              tmpStartStoppedBundles.add(bundleData);
            }

            if (bundleBecameStopped(bundleData, existingBundle)) {
              tmpStopStartedBundles.add(bundleData);
            }
          }

          fillStartLevelChangeWhereNecessary(bundleData, existingBundle,
              tmpSetInitialStartLevelOnBundles, tmpSetStartLevelFromInitialBundles,
              tmpLowerStartLevelOnBundles, tmpHigherStartLevelOnBundles, tmpIfInitialChanges);
        }
      }
    }

    this.uninstallBundles = new HashSet<>(bundleByLocation.values());
    this.installBundles = new HashSet<>(installBundleMap.values());
    this.updateBundles = tmpUpdateBundles;
    this.startStoppedBundles = tmpStartStoppedBundles;
    this.stopStartedBundles = tmpStopStartedBundles;
    this.lowerStartLevelOnBundles = tmpLowerStartLevelOnBundles;
    this.higherStartLevelOnBundles = tmpHigherStartLevelOnBundles;
    this.setInitialStartLevelOnBundles = tmpSetInitialStartLevelOnBundles;
    this.setStartLevelFromInitialBundles = tmpSetStartLevelFromInitialBundles;
    this.changeStartLevelIfInitialBundleStartLevelChangesOnBundles = tmpIfInitialChanges;
    this.lowestStartLevel = resolveLowestStartLevel();
  }

  private boolean bundleBecameStarted(final BundleDataType bundleData,
      final BundleDataType existingBundle) {
    return OSGiActionType.INSTALL.equals(existingBundle.getAction())
        && OSGiActionType.START.equals(bundleData.getAction());
  }

  private boolean bundleBecameStopped(final BundleDataType bundleData,
      final BundleDataType existingBundle) {
    return OSGiActionType.START.equals(existingBundle.getAction())
        && OSGiActionType.INSTALL.equals(bundleData.getAction());
  }

  private boolean bundleContentChanged(final ArtifactType newArtifact,
      final File environmentRootFolder,
      final PredefinedRepoArtifactResolver artifactResolver,
      final ArtifactHandlerManager artifactHandlerManager) throws MojoExecutionException {

    Artifact resolvedArtifact =
        resolveArtifact(artifactResolver, newArtifact, artifactHandlerManager);
    File newArtifactFile = resolvedArtifact.getFile();
    File oldArtifactFile =
        PluginUtil.resolveArtifactAbsoluteFile(newArtifact, resolvedArtifact,
            environmentRootFolder);

    return contentDifferent(newArtifactFile, oldArtifactFile);
  }

  private boolean contentDifferent(final File newArtifactFile, final File oldArtifactFile)
      throws MojoExecutionException {
    if (!oldArtifactFile.exists()
        || (newArtifactFile.lastModified() == oldArtifactFile.lastModified()
            && newArtifactFile.length() == oldArtifactFile.length())) {
      return false;
    }
    try (InputStream newIn = new BufferedInputStream(new FileInputStream(newArtifactFile));
        InputStream oldIn = new BufferedInputStream(new FileInputStream(oldArtifactFile))) {

      int oldR = 0;
      int newR = 0;

      boolean changed = false;
      while (oldR >= 0 && newR >= 0 && !changed) {
        oldR = oldIn.read();
        newR = newIn.read();

        if (oldR != newR) {
          changed = true;
        }
      }
      return changed;
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Error during diff check on files: " + oldArtifactFile + ", " + newArtifactFile, e);
    }

  }

  private Map<String, BundleDataType> createExistingBundleByLocationMap(
      final EnvironmentType existingDistributedEnvironment) {

    Map<String, BundleDataType> result = new HashMap<>();
    if (existingDistributedEnvironment == null
        || existingDistributedEnvironment.getArtifacts() == null) {
      return result;
    }

    for (ArtifactType artifact : existingDistributedEnvironment.getArtifacts().getArtifact()) {
      BundleDataType bundleData = artifact.getBundle();
      if (bundleData != null
          && !OSGiActionType.NONE.equals(bundleData.getAction())) {

        result.put(bundleData.getLocation(), bundleData);
      }
    }
    return result;
  }

  private void fillStartLevelChangeWhereNecessary(final BundleDataType bundleData,
      final BundleDataType existingBundle,
      final List<BundleDataWithCurrentStartLevel> tmpSetInitialStartLevelOnBundles,
      final List<BundleDataType> tmpSetStartLevelFromInitialBundles,
      final List<BundleDataType> tmpLowerStartLevelOnBundles,
      final List<BundleDataType> tmpHigherStartLevelOnBundles,
      final List<BundleDataType> tmpIfInitialChanges) {

    Integer newStartLevel = bundleData.getStartLevel();
    Integer existingStartLevel = existingBundle.getStartLevel();
    if (!Objects.equals(newStartLevel, existingStartLevel)) {
      if (newStartLevel == null) {
        tmpSetInitialStartLevelOnBundles
            .add(new BundleDataWithCurrentStartLevel(bundleData, existingStartLevel));
      } else if (existingStartLevel == null) {
        tmpSetStartLevelFromInitialBundles.add(bundleData);
      } else if (newStartLevel.compareTo(existingStartLevel) > 0) {
        tmpHigherStartLevelOnBundles.add(bundleData);
      } else {
        tmpLowerStartLevelOnBundles.add(bundleData);
      }
    } else if (newStartLevel == null) {
      tmpIfInitialChanges.add(bundleData);
    }
  }

  private Artifact resolveArtifact(final PredefinedRepoArtifactResolver artifactResolver,
      final ArtifactType artifact, final ArtifactHandlerManager artifactHandlerManager)
      throws MojoExecutionException {

    String artifactType = artifact.getType();
    if (artifactType == null) {
      artifactType = "jar";
    }
    String extension = artifactHandlerManager.getArtifactHandler(artifactType).getExtension();
    ArtifactRequest artifactRequest = new ArtifactRequest();
    Artifact aetherArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
        artifact.getClassifier(), extension, artifact.getVersion());
    artifactRequest.setArtifact(aetherArtifact);

    Artifact resolvedArtifact = artifactResolver.resolve(artifactRequest);
    return resolvedArtifact;
  }

  private Integer resolveLowestStartLevel() {
    Integer tmpLowestStartLevel = null;
    tmpLowestStartLevel = resolveLowestStartLevel(this.installBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.uninstallBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.updateBundles, tmpLowestStartLevel);
    tmpLowestStartLevel = resolveLowestStartLevel(this.startStoppedBundles, tmpLowestStartLevel);
    tmpLowestStartLevel =
        resolveLowestStartLevel(this.lowerStartLevelOnBundles, tmpLowestStartLevel);

    tmpLowestStartLevel =
        resolveLowestStartLevel(this.setStartLevelFromInitialBundles, tmpLowestStartLevel);

    return tmpLowestStartLevel;
  }

  private Integer resolveLowestStartLevel(final Collection<BundleDataType> bundleDataCollection,
      final Integer pLowestStartLevel) {
    Integer tmpLowestStartLevel = pLowestStartLevel;
    for (BundleDataType bundleData : bundleDataCollection) {
      Integer startLevel = bundleData.getStartLevel();
      if (startLevel != null
          && (pLowestStartLevel == null || startLevel.compareTo(tmpLowestStartLevel) < 0)) {
        tmpLowestStartLevel = startLevel;
      }
    }
    return tmpLowestStartLevel;
  }
}
