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
   * Simple implementation of the {@link Runnable}. Responsible to send event to Google Analytics
   * server.
   */
  private class SendingEventRunnable implements Runnable {

    private final String analyticsReferer;

    private HttpClient client;

    private final String executedGoalName;

    private final String macAddressHash;

    /**
     * Constructor.
     *
     * @param analyticsReferer
     *          the name of the referer. That means who execute goal (example: eosgi-maven-plugin or
     *          eclipse-e4-plugin).
     * @param executedGoalName
     *          the name of the executed goal.
     * @param macAddressHash
     *          the MAC address hash to anonym_user_id dimension.
     */
    SendingEventRunnable(final String analyticsReferer, final String executedGoalName,
        final String macAddressHash) {
      this.analyticsReferer = analyticsReferer;
      this.executedGoalName = executedGoalName;
      this.macAddressHash = macAddressHash;
      client = new DefaultHttpClient();
    }

    @Override
    public void run() {
      HttpPost post = new HttpPost(GA_ENDPOINT);
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("v", "1")); // version
      params.add(new BasicNameValuePair("tid", TRACKING_ID)); // tracking id
      params.add(new BasicNameValuePair("cid", macAddressHash)); // client id
      params.add(new BasicNameValuePair("t", "event")); // hit type
      params.add(new BasicNameValuePair("ec", analyticsReferer)); // event category
      params.add(new BasicNameValuePair("ea", executedGoalName)); // event action
      params.add(new BasicNameValuePair("dimension1", macAddressHash)); // anonym_user_id
      try {
        UrlEncodedFormEntity entity =
            new UrlEncodedFormEntity(params, "UTF-8");
        post.setEntity(entity);
        client.execute(post);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final String DEFAULT_MAC_ADDRESS_HASH = "MAC_ADDRESS_ANONYM";

  private static final String GA_ENDPOINT =
      "http://www.google-analytics.com/collect?payload_data";

  private static final String TRACKING_ID = "UA-69304815-1";

  private final long analyticsWaitingTimeInMs;

  private final boolean disableTracking;

  private final ConcurrentMap<Long, Thread> sendingEvents = new ConcurrentHashMap<>();

  public GoogleAnalyticsTrackingServiceImpl(final long analyticsWaitingTimeInMs,
      final boolean disableTracking) {
    this.analyticsWaitingTimeInMs = analyticsWaitingTimeInMs;
    this.disableTracking = disableTracking;
  }

  @Override
  public void cancelEventSending(final long eventId) {
    Thread thread = sendingEvents.get(eventId);
    if ((thread != null) && thread.isAlive()) {

      try {
        thread.join(analyticsWaitingTimeInMs);
        if (thread.isAlive()) {
          thread.interrupt();
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }

    }
  }

  private String getMacAddressHash() {
    try {
      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
      if (networkInterfaces.hasMoreElements()) {
        NetworkInterface network = networkInterfaces.nextElement();
        byte[] macAddress = network.getHardwareAddress();
        byte[] encodedMacAddress = Base64.encodeBase64(macAddress);
        return new String(encodedMacAddress, "UTF-8");
      } else {
        return DEFAULT_MAC_ADDRESS_HASH;
      }
    } catch (SocketException | UnsupportedEncodingException e) {
      return DEFAULT_MAC_ADDRESS_HASH;
    }
  }

  private long getNextEventId() {
    return sendingEvents.size() + 1;
  }

  @Override
  public long sendEvent(final String analyticsReferer, final String executedGoalName) {
    SendingEventRunnable sendingEvent =
        new SendingEventRunnable(analyticsReferer, executedGoalName, getMacAddressHash());
    Thread thread = new Thread(sendingEvent);

    long eventId = getNextEventId();
    sendingEvents.put(eventId, thread);

    if (!disableTracking) {
      thread.start();
    }
    return eventId;
  }

}
