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

/**
 * Constants for EOSGi solutions.
 */
public final class EOSGiConstants {

  public static final String ARTIFACT_PROPERTY_BUNDLE_ACTION = "bundle.action";

  public static final String ARTIFACT_PROPERTY_BUNDLE_LOCATION = "bundle.location";

  public static final String ARTIFACT_PROPERTY_BUNDLE_SYMBOLICNAME = "bundle.symbolicName";

  public static final String ARTIFACT_PROPERTY_BUNDLE_VERSION = "bundle.version";

  public static final String BUNDLE_ACTION_INSTALL = "install";

  public static final String BUNDLE_ACTION_NONE = "none";

  public static final String BUNDLE_ACTION_START = "start";

  private EOSGiConstants() {
  }
}
