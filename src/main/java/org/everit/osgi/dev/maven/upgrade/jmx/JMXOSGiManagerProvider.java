package org.everit.osgi.dev.maven.upgrade.jmx;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Generated;

import org.apache.maven.plugin.logging.Log;
import org.everit.osgi.dev.maven.DistMojo;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class JMXOSGiManagerProvider {

  private static class EnvironmentRuntimeInfo {
    String jmxServiceURL;

    File userDir;

    @Override
    @Generated("eclipse")
    public boolean equals(final Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      EnvironmentRuntimeInfo other = (EnvironmentRuntimeInfo) obj;
      if (jmxServiceURL == null) {
        if (other.jmxServiceURL != null)
          return false;
      } else if (!jmxServiceURL.equals(other.jmxServiceURL))
        return false;
      if (userDir == null) {
        if (other.userDir != null)
          return false;
      } else if (!userDir.equals(other.userDir))
        return false;
      return true;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((jmxServiceURL == null) ? 0 : jmxServiceURL.hashCode());
      result = prime * result + ((userDir == null) ? 0 : userDir.hashCode());
      return result;
    }

  }

  private final Map<String, Set<EnvironmentRuntimeInfo>> environments;

  public JMXOSGiManagerProvider(final Log log) {
    environments = new HashMap<>();

    List<VirtualMachineDescriptor> virtualMachines = VirtualMachine.list();
    for (VirtualMachineDescriptor virtualMachineDescriptor : virtualMachines) {
      try {
        VirtualMachine virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
        try {
          processVirtualMachine(virtualMachine);
        } finally {
          virtualMachine.detach();
        }

      } catch (AttachNotSupportedException | IOException | AgentInitializationException
          | AgentLoadException e) {
        log.error(
            "Error during communicating to JVM to check if it is an EOSGi environment: "
                + virtualMachineDescriptor.id() + " - " + virtualMachineDescriptor.displayName(),
            e);
      }
    }
  }

  public String getJmxURLForEnvironment(final String environmentId, final File environmentRootDir) {
    Set<EnvironmentRuntimeInfo> environmentInfos = environments.get(environmentId);
    if (environmentInfos == null) {
      return null;
    }

    for (EnvironmentRuntimeInfo environmentRuntimeInfo : environmentInfos) {
      if (isParentOrSameDir(environmentRootDir, environmentRuntimeInfo.userDir)) {
        return environmentRuntimeInfo.jmxServiceURL;
      }
    }
    return null;
  }

  private boolean isParentOrSameDir(final File environmentRootDir, final File userDir) {
    File currentDir = userDir;
    while (currentDir != null) {
      if (currentDir.equals(environmentRootDir)) {
        return true;
      }
      currentDir = currentDir.getParentFile();
    }
    return false;
  }

  private void loadMangementAgent(final VirtualMachine virtualMachine,
      final Properties systemProperties)
      throws AgentLoadException, AgentInitializationException, IOException {
    String javaHome = systemProperties.getProperty("java.home");
    String agent = javaHome + File.separator + "lib" + File.separator + "management-agent.jar";
    virtualMachine.loadAgent(agent);
  }

  private void processVirtualMachine(final VirtualMachine virtualMachine)
      throws IOException, AgentLoadException, AgentInitializationException {

    Properties systemProperties = virtualMachine.getSystemProperties();

    String environmentId = systemProperties.getProperty(DistMojo.SYSPROP_ENVIRONMENT_ID);
    if (environmentId == null) {
      return;
    }

    String jmxURL =
        readAgentProperty(virtualMachine, "com.sun.management.jmxremote.localConnectorAddress");

    if (jmxURL == null) {
      loadMangementAgent(virtualMachine, systemProperties);
      jmxURL =
          readAgentProperty(virtualMachine, "com.sun.management.jmxremote.localConnectorAddress");
    }

    if (jmxURL == null) {
      return;
    }

    String userDir = String.valueOf(systemProperties.get("user.dir"));

    EnvironmentRuntimeInfo environmentRuntimeInfo = new EnvironmentRuntimeInfo();
    environmentRuntimeInfo.jmxServiceURL = jmxURL;
    environmentRuntimeInfo.userDir = new File(userDir);

    Set<EnvironmentRuntimeInfo> environmentInfos = environments.get(environmentId);
    if (environmentInfos == null) {
      environmentInfos = new HashSet<>();
      environments.put(environmentId, environmentInfos);
    }
    environmentInfos.add(environmentRuntimeInfo);
  }

  private String readAgentProperty(final VirtualMachine virtualMachine, final String propertyName)
      throws IOException {
    String propertyValue = null;
    Properties agentProperties = virtualMachine.getAgentProperties();
    propertyValue = agentProperties.getProperty(propertyName);
    return propertyValue;
  }
}
