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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

/**
 * Implementation of {@link EventTrackable} interface.
 */
public class EventTrackableImpl implements EventTrackable {

  private static final String GA_ENDPOINT =
      "http://www.google-analytics.com/collect?payload_data";

  private static final String TRACKING_ID = "UA-69304815-1";

  private final HttpClient client = new DefaultHttpClient();

  @Override
  public void trackEventToGoogleAnalytics(final String categoryName, final String goalName,
      final String macAddressHash) {
    System.out.println("----------------- send ------------");
    HttpPost post = new HttpPost(GA_ENDPOINT);
    List<NameValuePair> params = new ArrayList<>();
    params.add(new BasicNameValuePair("v", "1")); // version
    params.add(new BasicNameValuePair("tid", TRACKING_ID)); // tracking id
    params.add(new BasicNameValuePair("cid", macAddressHash)); // client id
    params.add(new BasicNameValuePair("t", "event")); // hit type
    params.add(new BasicNameValuePair("ec", categoryName)); // event category
    params.add(new BasicNameValuePair("ea", goalName)); // event action
    params.add(new BasicNameValuePair("anonym_user_id", macAddressHash));
    try {
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, "UTF-8");
      post.setEntity(entity);
      HttpResponse response = client.execute(post);
      System.out
          .println("+++++++++++++++++++++++++++++++++ " + response.getStatusLine().toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

  }

}
