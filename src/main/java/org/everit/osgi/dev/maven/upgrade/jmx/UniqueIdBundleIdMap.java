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
package org.everit.osgi.dev.maven.upgrade.jmx;

import java.util.HashMap;
import java.util.Map;

public class UniqueIdBundleIdMap {

  private Map<String, Long> bundleIdByUniqueId = new HashMap<>();

  private Map<Long, String> uniqueIdByBundleId = new HashMap<>();

  public Long getBundleId(final String uniqueId) {
    return bundleIdByUniqueId.get(uniqueId);
  }

  public String getUniqueId(final Long bundleId) {
    return uniqueIdByBundleId.get(bundleId);
  }

  public void put(final String uniqueId, final Long bundleId) {
    bundleIdByUniqueId.put(uniqueId, bundleId);
    uniqueIdByBundleId.put(bundleId, uniqueId);
  }

  public void removeByUniqueId(final String uniqueId) {
    Long bundleId = bundleIdByUniqueId.remove(uniqueId);
    if (bundleId != null) {
      uniqueIdByBundleId.remove(bundleId);
    }
  }
}
