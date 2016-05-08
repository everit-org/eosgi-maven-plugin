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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.maven.plugin.logging.Log;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.BundleDataType;
import org.everit.osgi.dev.maven.upgrade.RemoteOSGiManager;
import org.everit.osgi.dev.maven.upgrade.RuntimeBundleInfo;
import org.osgi.framework.Bundle;
import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;

/**
 * The JMX implementation of the {@link RemoteOSGiManager}.
 */
public class JMXOSGiManager implements RemoteOSGiManager {

  private static final ObjectName BUNDLE_STATE_MBEAN_FILTER;

  private static final ObjectName FRAMEWORK_MBEAN_MBEAN_FILTER;

  static {
    try {
      FRAMEWORK_MBEAN_MBEAN_FILTER = new ObjectName(FrameworkMBean.OBJECTNAME + ",*");
      BUNDLE_STATE_MBEAN_FILTER = new ObjectName(BundleStateMBean.OBJECTNAME + ",*");
    } catch (MalformedObjectNameException e) {
      throw new IllegalStateException(e);
    }
  }

  private final BundleStateMBean bundleStateMBean;

  private final FrameworkMBean frameworkMBean;

  private final JMXConnector jmxConnector;

  private final Log log;

  private final UniqueIdBundleIdMap uniqueIdBundleIdMap;

  /**
   * Constructor.
   */
  public JMXOSGiManager(final String jmxServiceURL, final Log log)
      throws IOException, InstanceNotFoundException, IntrospectionException, ReflectionException {

    this.log = log;

    JMXServiceURL url = new JMXServiceURL(jmxServiceURL);
    jmxConnector = JMXConnectorFactory.connect(url, null);
    MBeanServerConnection mbsc = jmxConnector.getMBeanServerConnection();

    Set<ObjectName> frameworkMBeanONs = mbsc.queryNames(FRAMEWORK_MBEAN_MBEAN_FILTER, null);
    Set<ObjectName> bundleStateMBeanONs = mbsc.queryNames(BUNDLE_STATE_MBEAN_FILTER, null);

    if (frameworkMBeanONs.size() != 1) {
      throw new InstanceNotFoundException("Exactly one FrameworkMBean must be available in the "
          + "JMX registry. Currently [" + frameworkMBeanONs.size() + "] available.");
    }

    if (bundleStateMBeanONs.size() != 1) {
      throw new InstanceNotFoundException("Exactly one BundleStateMBean must be available in the "
          + "JMX registry. Currently [" + bundleStateMBeanONs.size() + "] available.");
    }

    ObjectName framewotkMBeanON = frameworkMBeanONs.iterator().next();
    ObjectName bundleStateMBeanON = bundleStateMBeanONs.iterator().next();

    mbsc.getMBeanInfo(framewotkMBeanON);
    mbsc.getMBeanInfo(bundleStateMBeanON);

    frameworkMBean = JMX.newMBeanProxy(mbsc, framewotkMBeanON, FrameworkMBean.class);
    bundleStateMBean = JMX.newMBeanProxy(mbsc, bundleStateMBeanON, BundleStateMBean.class);

    uniqueIdBundleIdMap = getUniqueIdBundleIdMap();

  }

  @Override
  public void close() {
    try {
      jmxConnector.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean contains(final long[] dependencyClosure, final long bundleId) {
    int searchResult = Arrays.binarySearch(dependencyClosure, bundleId);
    return searchResult > 0;
  }

  private RuntimeBundleInfo convertCompositeDataToRuntimeBundleInfo(final Object value) {
    CompositeData compositeData = (CompositeData) value;
    Long bundleId = (Long) compositeData.get(BundleStateMBean.IDENTIFIER);
    String symbolicName = (String) compositeData.get(BundleStateMBean.SYMBOLIC_NAME);
    String version = (String) compositeData.get(BundleStateMBean.VERSION);
    String state = (String) compositeData.get(BundleStateMBean.STATE);
    RuntimeBundleInfo runtimeBundleInfo = new RuntimeBundleInfo(bundleId, symbolicName, version,
        convertStringToIntState(state));
    return runtimeBundleInfo;
  }

  private int convertStringToIntState(final String state) {
    int result;
    switch (state) {
      case BundleStateMBean.ACTIVE:
        result = Bundle.ACTIVE;
        break;
      case BundleStateMBean.INSTALLED:
        result = Bundle.INSTALLED;
        break;
      case BundleStateMBean.RESOLVED:
        result = Bundle.RESOLVED;
        break;
      case BundleStateMBean.STARTING:
        result = Bundle.STARTING;
        break;
      case BundleStateMBean.STOPPING:
        result = Bundle.STOPPING;
        break;
      case BundleStateMBean.UNINSTALLED:
        result = Bundle.UNINSTALLED;
        break;
      default:
        throw new RuntimeException("Unknown state: " + state);
    }
    return result;
  }

  private String createUniqueIdentifier(final BundleDataType bundleDataType) {
    String symbolicName = bundleDataType.getSymbolicName();
    String version = bundleDataType.getVersion();
    return createUniqueIdentifier(symbolicName, version);
  }

  private String createUniqueIdentifier(final String symbolicName, final String version) {
    return symbolicName + "@" + version;
  }

  @Override
  public RuntimeBundleInfo[] getDependencyClosure(final BundleDataType... bundleDataArray) {
    long[] bundleIds = resolveBundleIdentifiers(bundleDataArray, "getDependencyClosure");

    try {
      long[] dependencyClosure = frameworkMBean.getDependencyClosure(bundleIds);
      if (dependencyClosure.length == 0) {
        return new RuntimeBundleInfo[0];
      }
      Arrays.sort(dependencyClosure);
      Collection<RuntimeBundleInfo> result = getRuntimeBundleInfoCollection();

      for (Iterator<RuntimeBundleInfo> iterator = result.iterator(); iterator.hasNext();) {
        RuntimeBundleInfo runtimeBundleInfo = iterator.next();

        if (!contains(dependencyClosure, runtimeBundleInfo.bundleId)) {
          iterator.remove();
        }
      }
      return result.toArray(new RuntimeBundleInfo[0]);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getFrameworkStartLevel() {
    try {
      return frameworkMBean.getFrameworkStartLevel();
    } catch (IOException e) {
      throw new RuntimeException();
    }
  }

  @Override
  public int getInitialBundleStartLevel() {
    try {
      return frameworkMBean.getInitialBundleStartLevel();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public RuntimeBundleInfo[] getRuntimeBundleInfoArray() {
    return getRuntimeBundleInfoCollection().toArray(new RuntimeBundleInfo[0]);
  }

  private Collection<RuntimeBundleInfo> getRuntimeBundleInfoCollection() {
    TabularData tabularData;
    try {
      tabularData =
          bundleStateMBean.listBundles(BundleStateMBean.IDENTIFIER, BundleStateMBean.SYMBOLIC_NAME,
              BundleStateMBean.VERSION, BundleStateMBean.STATE);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    List<RuntimeBundleInfo> result = new ArrayList<>();

    Collection<?> values = tabularData.values();
    for (Object value : values) {
      RuntimeBundleInfo runtimeBundleInfo = convertCompositeDataToRuntimeBundleInfo(value);

      result.add(runtimeBundleInfo);
    }
    return result;
  }

  private UniqueIdBundleIdMap getUniqueIdBundleIdMap() {

    UniqueIdBundleIdMap result = new UniqueIdBundleIdMap();

    TabularData tabularData;
    try {
      tabularData = bundleStateMBean.listBundles();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Collection<?> values = tabularData.values();
    for (Object value : values) {

      CompositeData compositeData = (CompositeData) value;

      String symbolicName = (String) compositeData.get(BundleStateMBean.SYMBOLIC_NAME);
      String version = (String) compositeData.get(BundleStateMBean.VERSION);
      Long bundleIdentifier = (Long) compositeData.get(BundleStateMBean.IDENTIFIER);

      String uniqueIdentifier = createUniqueIdentifier(symbolicName, version);

      result.put(uniqueIdentifier, bundleIdentifier);
    }

    return result;
  }

  @Override
  public void installBundles(final BundleDataType... bundleDataTypes) {

    if ((bundleDataTypes == null) || (bundleDataTypes.length == 0)) {
      return;
    }

    try {
      for (BundleDataType bundleDataType : bundleDataTypes) {
        String bundleLocation = bundleDataType.getLocation();
        long bundleIdentifier = frameworkMBean.installBundle(bundleLocation);
        uniqueIdBundleIdMap.put(createUniqueIdentifier(bundleDataType), bundleIdentifier);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void refresh() {
    try {
      frameworkMBean.refreshBundlesAndWait(null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void resolveAll() {
    try {
      frameworkMBean.resolveBundles(null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private long[] resolveBundleIdentifiers(final BundleDataType[] bundleDataArray,
      final String actionNameForMessages) {
    long[] bundleIdentifierArray = new long[bundleDataArray.length];
    int i = 0;

    for (BundleDataType bundleData : bundleDataArray) {
      Long bundleIdentifier = uniqueIdBundleIdMap.getBundleId(createUniqueIdentifier(bundleData));
      if (bundleIdentifier != null) {
        bundleIdentifierArray[i] = bundleIdentifier;
        i++;
      } else {
        log.warn("'" + actionNameForMessages
            + "' action cannot be executed on bundle as it is not found on the container: "
            + bundleData.getSymbolicName() + ":" + bundleData.getVersion());
      }
    }

    return (i == bundleDataArray.length) ? bundleIdentifierArray
        : Arrays.copyOf(bundleIdentifierArray, i);
  }

  @Override
  public void setBundleStartLevel(final BundleDataType bundleData, final int newlevel) {
    Long bundleIdentifier = uniqueIdBundleIdMap.getBundleId(createUniqueIdentifier(bundleData));
    if (bundleIdentifier == null) {
      log.warn("Cannot set bundle start level as it is not found in the container: "
          + bundleData.getSymbolicName() + ":" + bundleData.getVersion());
    } else {
      try {
        frameworkMBean.setBundleStartLevel(bundleIdentifier, newlevel);
      } catch (IOException e) {
        throw new RuntimeException();
      }
    }

  }

  @Override
  public void setFrameworkStartLevel(final int newlevel) {
    try {
      frameworkMBean.setFrameworkStartLevel(newlevel);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void setInitialBundleStartLevel(final int startLevel) {
    try {
      frameworkMBean.setInitialBundleStartLevel(startLevel);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void startBundles(final BundleDataType... bundleDataArray) {
    long[] bundleIdentifiers = resolveBundleIdentifiers(bundleDataArray, "start");
    if (bundleIdentifiers.length > 0) {
      try {
        frameworkMBean.startBundles(bundleIdentifiers);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void stopBundles(final BundleDataType... bundleDataArray) {
    long[] bundleIdentifiers = resolveBundleIdentifiers(bundleDataArray, "stop");
    if (bundleIdentifiers.length > 0) {
      try {
        frameworkMBean.stopBundles(bundleIdentifiers);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void uninstallBundles(final BundleDataType... bundleDataArray) {
    if ((bundleDataArray == null) || (bundleDataArray.length == 0)) {
      return;
    }

    try {
      for (BundleDataType bundleData : bundleDataArray) {
        String uniqueIdentifier = createUniqueIdentifier(bundleData);
        Long bundleIdentifier = uniqueIdBundleIdMap.getBundleId(uniqueIdentifier);
        if (bundleIdentifier == null) {
          throw new RuntimeException("Could not uninstall bundle as it does not exist: "
              + bundleData.getSymbolicName() + ":" + bundleData.getVersion());
        }
        frameworkMBean.uninstallBundle(bundleIdentifier);
        uniqueIdBundleIdMap.removeByUniqueId(uniqueIdentifier);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void updateBundles(final BundleDataType... bundleDataArray) {
    long[] bundleIdentifiers = resolveBundleIdentifiers(bundleDataArray, "stop");
    if (bundleIdentifiers.length > 0) {
      try {
        frameworkMBean.updateBundles(bundleIdentifiers);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

}
