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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

/**
 * Implementation of {@link GoogleAnalyticsTrackingService}.
 */
public class GoogleAnalyticsTrackingServiceImpl implements GoogleAnalyticsTrackingService {

  /**
   * Simple implementation of the {@link Runnable}. Responsible to send event the Google Analytics
   * server.
   */
  private class EventSender implements Runnable {

    private final String analyticsReferer;

    private final String executedGoalName;

    private final HttpClient httpClient;

    private final String macAddressHash;

    /**
     * Constructor.
     *
     * @param analyticsReferer
     *          the name of the referer that means who execute goal (example: eosgi-maven-plugin or
     *          eclipse-e4-plugin).
     * @param executedGoalName
     *          the name of the executed goal.
     * @param macAddressHash
     *          the MAC address hash to anonym_user_id dimension.
     */
    EventSender(final String analyticsReferer, final String executedGoalName,
        final String macAddressHash) {
      this.analyticsReferer = analyticsReferer;
      this.executedGoalName = executedGoalName;
      this.macAddressHash = macAddressHash;
      httpClient = new DefaultHttpClient();
    }

    @Override
    public void run() {
      HttpPost post = new HttpPost(GA_ENDPOINT);
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("v", "1")); // version
      params.add(new BasicNameValuePair("tid", trackingId)); // tracking id
      params.add(new BasicNameValuePair("cid", macAddressHash)); // client id
      params.add(new BasicNameValuePair("t", "event")); // hit type
      params.add(new BasicNameValuePair("ec", analyticsReferer)); // event category
      params.add(new BasicNameValuePair("ea", executedGoalName)); // event action
      params.add(new BasicNameValuePair("cd1", macAddressHash)); // anonym_user_id
      try {
        UrlEncodedFormEntity entity =
            new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        httpClient.execute(post);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final String GA_ENDPOINT =
      "http://www.google-analytics.com/collect?payload_data";

  private static final String UNKNOWN_MAC_ADDRESS = "UNKNOWN_MAC_ADDRESS";

  private final long analyticsWaitingTimeInMs;

  private final ConcurrentMap<Long, Thread> sendingEvents = new ConcurrentHashMap<>();

  private final boolean skipAnalytics;

  private final String trackingId;

  /**
   * Constructor.
   *
   * @param trackingId
   *          the id of tracking.
   * @param analyticsWaitingTimeInMs
   *          the waiting time to send the analytics to Google Analytics server.
   * @param skipAnalytics
   *          skip analytics tracking or not.
   */
  public GoogleAnalyticsTrackingServiceImpl(final String trackingId,
      final long analyticsWaitingTimeInMs,
      final boolean skipAnalytics) {
    this.trackingId = trackingId;
    this.analyticsWaitingTimeInMs = analyticsWaitingTimeInMs;
    this.skipAnalytics = skipAnalytics;
  }

  @Override
  public void cancelEventSending(final long eventId) {
    if (skipAnalytics) {
      return;
    }

    Thread thread = sendingEvents.get(eventId);
    if ((thread == null) || !thread.isAlive()) {
      return;
    }

    try {
      thread.join(analyticsWaitingTimeInMs);
      if (thread.isAlive()) {
        thread.interrupt();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

  }

  private String getMacAddressHash() {
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      if (networkInterfaces.hasMoreElements()) {
        NetworkInterface network = networkInterfaces.nextElement();
        byte[] macAddress = network.getHardwareAddress();
        MessageDigest messageDigest = MessageDigest.getInstance("SHA");
        messageDigest.update(macAddress);
        byte[] macAddressHash = messageDigest.digest();
        byte[] encodedMacAddressHash = Base64.encodeBase64(macAddressHash);
        return new String(encodedMacAddressHash, "UTF-8");
      } else {
        return UNKNOWN_MAC_ADDRESS;
      }
    } catch (SocketException | UnsupportedEncodingException | NoSuchAlgorithmException e) {
      return UNKNOWN_MAC_ADDRESS;
    }
  }

  @Override
  public long sendEvent(final String analyticsReferer, final String executedGoalName) {
    if (skipAnalytics) {
      return -1;
    }

    EventSender sendingEvent =
        new EventSender(analyticsReferer, executedGoalName, getMacAddressHash());
    Thread thread = new Thread(sendingEvent);

    long eventId = thread.getId();
    sendingEvents.put(eventId, thread);

    thread.start();

    return eventId;
  }

}
