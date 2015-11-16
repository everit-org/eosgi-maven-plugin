package org.everit.osgi.dev.maven.upgrade.jmx;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
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

  private final BundleStateMBean bundleStateMBean;

  private final FrameworkMBean frameworkMBean;

  private final JMXConnector jmxConnector;

  private final Log log;

  /**
   * Constructor.
   */
  public JMXOSGiManager(final int port, final Log log) {

    this.log = log;

    try {

      JMXServiceURL url = new JMXServiceURL(
          "service:jmx:rmi:///jndi/rmi://:" + port + "/jmxrmi");
      jmxConnector = JMXConnectorFactory.connect(url, null);
      MBeanServerConnection mbsc = jmxConnector.getMBeanServerConnection();

      frameworkMBean = JMX.newMBeanProxy(mbsc,
          new ObjectName(FrameworkMBean.OBJECTNAME), FrameworkMBean.class);

      bundleStateMBean = JMX.newMBeanProxy(mbsc,
          new ObjectName(BundleStateMBean.OBJECTNAME), BundleStateMBean.class);

    } catch (MalformedObjectNameException | IOException e) {
      throw new RuntimeException(e);
    }

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

  @Override
  public void installBundle(final BundleDataType bundleDataType) {

    if (bundleDataType == null) {
      return;
    }

    if (OSGiActionType.NONE.equals(bundleDataType.getAction())) {
      uninstallBundles(bundleDataType);
      return;
    }

    try {

      String bundleLocation = bundleDataType.getLocation();
      long bundleIdentifier = frameworkMBean.installBundle(bundleLocation);

      Integer startLevel = bundleDataType.getStartLevel();
      if (startLevel != null) {
        frameworkMBean.setBundleStartLevel(bundleIdentifier, startLevel);
      }

      frameworkMBean.startBundle(bundleIdentifier);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void uninstallBundles(final BundleDataType... bundleDataTypes) {

    if ((bundleDataTypes == null) || (bundleDataTypes.length == 0)) {
      return;
    }

    try {

      Map<String, Long> identifiers = new HashMap<>();

      TabularData tabularData = bundleStateMBean.listBundles();
      Collection<?> values = tabularData.values();
      for (Object value : values) {

        CompositeData compositeData = (CompositeData) value;

        String symbolicName = (String) compositeData.get(BundleStateMBean.SYMBOLIC_NAME);
        String version = (String) compositeData.get(BundleStateMBean.VERSION);
        Long bundleIdentifier = (Long) compositeData.get(BundleStateMBean.IDENTIFIER);

        String uniqueIdentifier = createUniqueIdentifier(symbolicName, version);

        identifiers.put(uniqueIdentifier, bundleIdentifier);
      }

      for (BundleDataType bundleDataType : bundleDataTypes) {

        String symbolicName = bundleDataType.getSymbolicName();
        String version = bundleDataType.getVersion();

        String uniqueIdentifier = createUniqueIdentifier(symbolicName, version);

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

}
