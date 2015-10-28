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
 * Interface for Google Analytics tracking.
 */
public interface GoogleAnalyticsTrackingService {

  /**
   * Send an Event Tracking message to Google Analytics.
   *
   * @param analyticsReferer
   *          the name of the referer that means who execute goal (example: eosgi-maven-plugin or
   *          eclipse-e4-plugin).
   * @param executedGoalName
   *          the name of the executed goal.
   * @return the unique identifier the event. The event identifier is positive. If event identifier
   *         is -1 that means skip tracking and not send event to the Google Analytics server.
   */
  long sendEvent(String analyticsReferer, String executedGoalName);

  /**
   * Wait for event sending. Find event and wait to finished sending.
   *
   * @param eventId
   *          The event identifier what we would like to wait sending.
   */
  void waitForEventSending(long eventId);
}
