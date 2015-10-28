package org.everit.osgi.dev.maven.statistic;

/**
 * Interface for Google Analytics tracking.
 */
public interface GoogleAnalyticsTrackingService {

  /**
   * Cancel sending event process based on event id.
   *
   * @param eventId
   *          the id of the event that want so cancel.
   */
  void cancelEventSending(long eventId);

  /**
   * Send event tracking message to Google Analytics.
   *
   * @param analyticsReferer
   *          the name of the referer. That means who execute goal (example: eosgi-maven-plugin or
   *          eclipse-e4-plugin).
   * @param executedGoalName
   *          the name of the executed goal.
   * @return the unique event id the send event.
   */
  long sendEvent(String analyticsReferer, String executedGoalName);
}
