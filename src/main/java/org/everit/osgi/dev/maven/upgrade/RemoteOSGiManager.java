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
package org.everit.osgi.dev.maven.upgrade;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;

/**
 * Interface to manage an OSGi Framework remotely.
 */
public interface RemoteOSGiManager extends Closeable {

  @Override
  void close();

  RuntimeBundleInfo[] getDependencyClosure(Collection<BundleSNV> bundleSNVs);

  int getFrameworkStartLevel();

  int getInitialBundleStartLevel();

  RuntimeBundleInfo[] getRuntimeBundleInfoArray();

  void installBundles(Map<BundleSNV, String> bundleSNVAndLocationMap);

  void refresh();

  void resolveAll();

  void setBundleStartLevel(BundleSNV bundleSNV, int newlevel);

  void setFrameworkStartLevel(int startLevel);

  void setInitialBundleStartLevel(int startLevel);

  void startBundles(Collection<BundleSNV> bundleSNVs);

  void stopBundles(Collection<BundleSNV> bundleSNVs);

  void uninstallBundles(Collection<BundleSNV> bundleSNVs);

  void updateBundles(Collection<BundleSNV> bundleSNVs);
}
