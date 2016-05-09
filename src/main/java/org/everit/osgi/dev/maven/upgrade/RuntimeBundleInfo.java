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

/**
 * Runtime information of a bundle.
 */
public class RuntimeBundleInfo {

  public long bundleId;

  public int state;

  public String symbolicName;

  public String version;

  /**
   * Constructor.
   *
   * @param bundleId
   *          The id of the bundle.
   * @param symbolicName
   *          The symbolic name of the bundle.
   * @param version
   *          The version of the bundle.
   * @param state
   *          The state of the bundle.
   */
  public RuntimeBundleInfo(final long bundleId, final String symbolicName, final String version,
      final int state) {
    this.bundleId = bundleId;
    this.symbolicName = symbolicName;
    this.version = version;
    this.state = state;
  }

}
