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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
import org.everit.osgi.dev.eosgi.dist.schema.xsd.OSGiActionType;
import org.everit.osgi.dev.maven.upgrade.RemoteOSGiManager;
import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;

/**
 * The JMX implementation of the {@link RemoteOSGiManager}.
 */
public class JMXOSGiManager implements RemoteOSGiManager {

  private static final ObjectName BUNDLE_STATE_MBEAN_FILTER;

  private static final ObjectName FRAMEWORK_MBEAN_MBEAN_FILTER;

  private static final String REFERENCE = "reference:";

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

  /**
   * Constructor.
   */
  public JMXOSGiManager(final int port, final Log log)
      throws IOException, InstanceNotFoundException, IntrospectionException, ReflectionException {

    this.log = log;

    JMXServiceURL url = new JMXServiceURL(
        "service:jmx:rmi:///jndi/rmi://:" + port + "/jmxrmi");
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
  public void disconnect() {
    try {
      jmxConnector.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, Long> getBundleIdentifiers() {

    Map<String, Long> identifiers = new HashMap<>();

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

      identifiers.put(uniqueIdentifier, bundleIdentifier);
    }

    return identifiers;
  }

  @Override
  public void installBundles(final BundleDataType... bundleDataTypes) {

    if ((bundleDataTypes == null) || (bundleDataTypes.length == 0)) {
      return;
    }

    try {

      for (BundleDataType bundleDataType : bundleDataTypes) {

        if (OSGiActionType.NONE.equals(bundleDataType.getAction())) {

          uninstallBundles(bundleDataType);

        } else {

          String bundleLocation = bundleDataType.getLocation();
          long bundleIdentifier = frameworkMBean.installBundle(bundleLocation);

          Integer startLevel = bundleDataType.getStartLevel();
          if (startLevel != null) {
            frameworkMBean.setBundleStartLevel(bundleIdentifier, startLevel);
          }

          frameworkMBean.startBundle(bundleIdentifier);

        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void refresh() {

    try {

      frameworkMBean.refreshBundles(null);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void uninstallBundles(final BundleDataType... bundleDataTypes) {

    if ((bundleDataTypes == null) || (bundleDataTypes.length == 0)) {
      return;
    }

    Map<String, Long> identifiers = getBundleIdentifiers();

    try {

      for (BundleDataType bundleDataType : bundleDataTypes) {

        String uniqueIdentifier = createUniqueIdentifier(bundleDataType);

        Long bundleIdentifier = identifiers.get(uniqueIdentifier);

        if (bundleIdentifier != null) {
          frameworkMBean.uninstallBundle(bundleIdentifier);
        } else {
          log.warn("Bundle with symbolic name and version ["
              + uniqueIdentifier + "] cannot be uninstalled.");
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void updateBundles(final BundleDataType... bundleDataTypes) {

    if ((bundleDataTypes == null) || (bundleDataTypes.length == 0)) {
      return;
    }

    Map<String, Long> bundleIdentifiers = getBundleIdentifiers();

    try {

      for (BundleDataType bundleDataType : bundleDataTypes) {

        if (OSGiActionType.NONE.equals(bundleDataType.getAction())) {

          uninstallBundles(bundleDataType);

        } else {

          String uniqueIdentifier = createUniqueIdentifier(bundleDataType);
          long bundleIdentifier = bundleIdentifiers.get(uniqueIdentifier);

          String location = bundleDataType.getLocation();
          // TODO if (location.startsWith(REFERENCE)) {
          // location = location.substring(REFERENCE.length());
          // }
          frameworkMBean.updateBundleFromURL(bundleIdentifier, location);

        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

}
