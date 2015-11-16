package org.everit.osgi.dev.maven.upgrade;

import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;

/**
 * Interface to manage an OSGi Framework remotely.
 */
public interface RemoteOSGiManager {

  void disconnect();

  void installBundle(BundleDataType bundleDataType);

  void uninstallBundles(BundleDataType... bundleDataTypes);

}
