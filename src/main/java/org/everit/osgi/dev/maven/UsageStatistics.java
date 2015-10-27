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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.maven.plugin.logging.Log;

public class UsageStatistics implements Runnable {

  private static final String CATEGORY_ECLIPSE_E4_PLUGIN = "eclipse-e4-plugin";

  private static final String CATEGORY_EOSGI_MAVEN_PLUGIN = "eosgi-maven-plugin";

  private static final String GA_ENDPOINT =
      "http://www.google-analytics.com/collect?payload_data";

  private final boolean analyticsRefererEclipse;

  private final HttpClient client = new DefaultHttpClient();

  private final String goalName;

  private final Thread thread;

  public UsageStatistics(final boolean analyticsRefererEclipse, final String goalName,
      final Log log) {
    log.info("\n\nWe collect usage statictics and upload to google analytics. "
        + "Read more details from http://www.everit.org/eosgi-maven-plugin/.\n\n");

    this.analyticsRefererEclipse = analyticsRefererEclipse;
    this.goalName = goalName;
    thread = new Thread(this);
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
        return "ANONYM";
      }
    } catch (SocketException e) {
      return "ANONYM";
    }
  }

  @Override
  public void run() {
    trackEventToGoogleAnalytics();
  }

  public void shutdown(final long maxWaitingTimeInMs) {
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

  public void startSending() {
    thread.start();
  }

  /**
   * Posts an Event Tracking message to Google Analytics.
   */
  private void trackEventToGoogleAnalytics() {
    HttpPost post = new HttpPost(GA_ENDPOINT);
    String macAddressHash = getMacAddressHash();
    List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("v", "1"));
    params.add(new BasicNameValuePair("tid", "UA-69304815-1"));
    params.add(new BasicNameValuePair("cid", macAddressHash));
    params.add(new BasicNameValuePair("t", "event"));
    params.add(new BasicNameValuePair("ec", getCategory()));
    params.add(new BasicNameValuePair("ea", goalName));
    params.add(new BasicNameValuePair("anonym_user_id", macAddressHash));
    try {
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
      post.setEntity(entity);
      HttpResponse resp = client.execute(post);
      resp.getStatusLine();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}
