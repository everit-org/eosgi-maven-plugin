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

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Base64;
import java.util.Enumeration;

import org.apache.maven.plugin.logging.Log;

/**
 * Responsible to manage usage statistics.
 */
public class UsageAnalytics {

  /**
   * Simple {@link Runnable} implementation to call
   * {@link EventTrackable#trackEventToGoogleAnalytics} method.
   */
  private class TrackingRunnable implements Runnable {

    @Override
    public void run() {
      System.out.println("----------------- run ------------");
      new EventTrackableImpl()
          .trackEventToGoogleAnalytics(getCategory(), goalName, getMacAddressHash());
    }
  }

  private static final String CATEGORY_ECLIPSE_E4_PLUGIN = "eclipse-e4-plugin";

  private static final String CATEGORY_EOSGI_MAVEN_PLUGIN = "eosgi-maven-plugin";

  private static final String DEFAULT_MAC_ADDRESS_HASH = "MAC_ADDRESS_ANONYM";

  private final boolean analyticsRefererEclipse;

  private final String goalName;

  private final int maxWaitingTimeInMs = 10000;

  private final Thread thread;

  /**
   * Constructor.
   *
   * @param analyticsRefererEclipse
   *          the plugin called from eclipse or not.
   * @param goalName
   *          the executed goal name.
   * @param log
   *          the {@link Log} instance.
   */
  public UsageAnalytics(final boolean analyticsRefererEclipse, final String goalName,
      final Log log) {
    log.info("\n\nWe collect usage statictics and upload to google analytics. "
        + "Read more details from http://www.everit.org/eosgi-maven-plugin/.\n\n");

    this.analyticsRefererEclipse = analyticsRefererEclipse;
    System.out.println(analyticsRefererEclipse);
    this.goalName = goalName;
    System.err.println(goalName);
    thread = new Thread(new TrackingRunnable());
  }

  private String getCategory() {
    return analyticsRefererEclipse ? CATEGORY_ECLIPSE_E4_PLUGIN : CATEGORY_EOSGI_MAVEN_PLUGIN;
  }

  private String getMacAddressHash() {
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      if (networkInterfaces.hasMoreElements()) {
        NetworkInterface network = networkInterfaces.nextElement();
        byte[] macAddress = network.getHardwareAddress();
        return Base64.getEncoder().encodeToString(macAddress);
      } else {
        return DEFAULT_MAC_ADDRESS_HASH;
      }
    } catch (SocketException e) {
      return DEFAULT_MAC_ADDRESS_HASH;
    }
  }

  /**
   * Shutdown thread if necessary. Wait specific ({@link EventTrackableImpl#MAX_WAITING_TIME_IN_MS})
   * seconds to thread finish run, after force to stop thread.
   */
  public void shutdown() {
    System.out.println("----------------- shutdown ------------");
    if (thread.isAlive()) {

      try {
        thread.join(maxWaitingTimeInMs);
        if (thread.isAlive()) {
          thread.interrupt();
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

    }
  }

  /**
   * Start to send tracking information.
   */
  public void startSending() {
    System.out.println("----------------- start ------------");
    thread.start();
  }

}
