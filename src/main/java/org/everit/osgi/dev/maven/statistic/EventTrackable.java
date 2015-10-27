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
package org.everit.osgi.dev.maven.statistic;

/**
 * Interface for tracking that send event to Google Analytics.
 */
public interface EventTrackable {

  /**
   * Posts an Event Tracking message to Google Analytics.
   *
   * @param categoryName
   *          the name of the category (example: eosgi-maven-plugin or eclipse-e4-plugin)
   * @param goalName
   *          the name of the executed goal.
   * @param macAddressHash
   *          the MAC address hash.
   */
  void trackEventToGoogleAnalytics(String categoryName, String goalName, String macAddressHash);
}
