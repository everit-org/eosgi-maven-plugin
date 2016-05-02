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

import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;

/**
 * An implementation that does nothing.
 */
public class NoopRemoteOSGiManager implements RemoteOSGiManager {

  @Override
  public void close() {
    // Do nothing
  }

  @Override
  public void installBundles(final BundleDataType... bundleDataTypes) {
    // Do nothing

  }

  @Override
  public void refresh() {
    // Do nothing
  }

  @Override
  public void uninstallBundles(final BundleDataType... bundleDataTypes) {
    // Do nothing
  }

  @Override
  public void updateBundles(final BundleDataType... bundleDataType) {
    // Do nothing
  }

}
