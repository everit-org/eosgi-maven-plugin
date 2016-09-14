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
package org.everit.osgi.dev.maven.upgrade;

import javax.annotation.Generated;

public class BundleSNV {

  public final String symbolicName;

  public final String version;

  public BundleSNV(final String symbolicName, final String version) {
    this.symbolicName = symbolicName;
    this.version = version;
  }

  @Override
  @Generated("eclipse")
  public boolean equals(final Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    BundleSNV other = (BundleSNV) obj;
    if (symbolicName == null) {
      if (other.symbolicName != null)
        return false;
    } else if (!symbolicName.equals(other.symbolicName))
      return false;
    if (version == null) {
      if (other.version != null)
        return false;
    } else if (!version.equals(other.version))
      return false;
    return true;
  }

  @Override
  @Generated("eclipse")
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((symbolicName == null) ? 0 : symbolicName.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    return result;
  }

  @Override
  public String toString() {
    return "BundleSNV [symbolicName=" + symbolicName + ", version=" + version + "]";
  }

}
