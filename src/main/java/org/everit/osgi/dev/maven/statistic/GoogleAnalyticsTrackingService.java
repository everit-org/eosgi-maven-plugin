package org.everit.osgi.dev.maven.statistic;

/**
 * Interface for Google Analytics tracking.
 */
public interface GoogleAnalyticsTrackingService {

  /**
   * Send event tracking message to Google Analytics.
   *
   * @param analyticsReferer
   *          the name of the referer. That means who execute goal (example: eosgi-maven-plugin or
   *          eclipse-e4-plugin).
   * @param executedGoalName
   *          the name of the executed goal.
   */
  void sendEvent(String analyticsReferer, String executedGoalName);

  /**
   * Shutdown sending process.
   */
  void shutdown();
}
