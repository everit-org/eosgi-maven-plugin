package org.everit.osgi.dev.maven.util;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.ArtifactsType;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.DistributionPackageType;

public class BundleExecutionPlan {

  public final int lowestStartLevel;

  public final List<ArtifactType> removeBundles;

  public final List<ArtifactType> uninstallBundles;

  public final List<ArtifactType> updateBundles;

  public BundleExecutionPlan(final DistributionPackageType existingDistributionPackage,
      final ArtifactsType newArtifacts, final File environmentRootFolder) {

    Map<String, ArtifactType> bundleByLocation = new HashMap<>();
    // TODO Auto-generated constructor stub
  }
}
