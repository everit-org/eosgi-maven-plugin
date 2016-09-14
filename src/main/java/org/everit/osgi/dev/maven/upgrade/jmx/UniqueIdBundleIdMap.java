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

/**
 * A mapping of bundle unique ids and bundle ids. It is up to the caller library how to generate a
 * unique string for this mapping, but it must be unique. E.g.: A concatenation of symbolic name and
 * version.
 */
public class UniqueIdBundleIdMap<I> {

  private final Map<I, Long> bundleIdByUniqueId = new HashMap<>();

  private final Map<Long, I> uniqueIdByBundleId = new HashMap<>();

  public Long getBundleId(final I uniqueId) {
    return bundleIdByUniqueId.get(uniqueId);
  }

  public void put(final I uniqueId, final Long bundleId) {
    bundleIdByUniqueId.put(uniqueId, bundleId);
    uniqueIdByBundleId.put(bundleId, uniqueId);
  }

  /**
   * Removes an entry by the unique id of the map.
   *
   * @param uniqueId
   *          The unique id of the map.
   */
  public void removeByBundleId(final Long bundleId) {
    I uniqueId = uniqueIdByBundleId.remove(bundleId);
    if (uniqueId != null) {
      bundleIdByUniqueId.remove(uniqueId);
    }
  }
}
