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

import java.util.Collection;

/**
 * An implementation that does nothing.
 */
public class NoopRemoteOSGiManager implements RemoteOSGiManager {

  @Override
  public void close() {
    // Do nothing
  }

  @Override
  public RuntimeBundleInfo[] getDependencyClosure(final Collection<String> bundleLocations) {
    return new RuntimeBundleInfo[0];
  }

  @Override
  public int getFrameworkStartLevel() {
    return 0;
  }

  @Override
  public int getInitialBundleStartLevel() {
    return 0;
  }

  @Override
  public RuntimeBundleInfo[] getRuntimeBundleInfoArray() {
    return new RuntimeBundleInfo[0];
  }

  @Override
  public void installBundles(final Collection<String> bundleLocations) {
    // Do nothing

  }

  @Override
  public void refresh() {
    // Do nothing
  }

  @Override
  public void resolveAll() {
    // Do nothing
  }

  @Override
  public void setBundleStartLevel(final String bundleLocation, final int newlevel) {
    // Do nothing
  }

  @Override
  public void setFrameworkStartLevel(final int newlevel) {
    // Do nothing
  }

  @Override
  public void setInitialBundleStartLevel(final int startLevel) {
    // Do nothing
  }

  @Override
  public void startBundles(final Collection<String> bundleLocations) {
    // Do nothing
  }

  @Override
  public void stopBundles(final Collection<String> bundleLocations) {
    // Do nothing
  }

  @Override
  public void uninstallBundles(final Collection<String> bundleLocations) {
    // Do nothing
  }

  @Override
  public void updateBundles(final Collection<String> bundleLocations) {
    // Do nothing
  }

}
