/**
 * This file is part of Everit - Maven OSGi plugin.
 *
 * Everit - Maven OSGi plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Maven OSGi plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Maven OSGi plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.maven;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.eclipse.osgi.service.resolver.PlatformAdmin;
import org.eclipse.osgi.service.resolver.State;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * Analyses the environment settings based on different algorythms and provides useful tips how to
 * improve their configuration.
 * 
 * <p>
 * At the moment, this goal shows the unsatisfied dependencies in case no package is exported from
 * the JDK. This information can be useful to find out what should be specified for
 * <i>org.osgi.framework.system.packages</i> property and what could be added as a bundle.
 */
@Mojo(name = "analyse", requiresProject = true,
    requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.PACKAGE)
public class AnalyseMojo extends AbstractEOSGiMojo {

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    EnvironmentConfiguration[] environmentsToProcess = getEnvironmentsToProcess();

    for (EnvironmentConfiguration environment : environmentsToProcess) {
      List<DistributableArtifact> distributableArtifacts;
      try {
        distributableArtifacts = generateDistributableArtifacts(environment);
      } catch (MalformedURLException e) {
        throw new MojoExecutionException("Could not resolve dependent artifacts of project", e);
      }

      List<String> bundleLocations = new ArrayList<>();
      for (DistributableArtifact distributableArtifact : distributableArtifacts) {
        bundleLocations.add(resolveArtifactFileURI(distributableArtifact.getArtifact()));
      }
      diagnose(bundleLocations.toArray(new String[bundleLocations.size()]));
    }
  }

  private String resolveArtifactFileURI(final Artifact artifact) {
    File artifactFile = artifact.getFile();
    if (artifactFile != null) {
      try {
        return artifact.getFile().toURI().toURL().toExternalForm();
      } catch (MalformedURLException e) {
        getLog().error(e);
      }
    }
    return null;
  }

  /**
   * Map of plugin artifacts.
   */
  @Parameter(defaultValue = "${plugin.artifactMap}", required = true, readonly = true)
  protected Map<String, Artifact> pluginArtifactMap;

  private Framework startOSGiContainer(final String[] bundleLocations,
      final String tempDirPath) throws BundleException {
    FrameworkFactory frameworkFactory = ServiceLoader
        .load(FrameworkFactory.class).iterator().next();

    Map<String, String> config = new HashMap<String, String>();
    config.put("org.osgi.framework.system.packages", "");
    config.put("osgi.configuration.area", tempDirPath);
    config.put("osgi.baseConfiguration.area", tempDirPath);
    config.put("osgi.sharedConfiguration.area", tempDirPath);
    config.put("osgi.instance.area", tempDirPath);
    config.put("osgi.user.area", tempDirPath);
    config.put("osgi.hook.configurators.exclude",
        "org.eclipse.core.runtime.internal.adaptor.EclipseLogHook");

    resetFrameworkProperties();
    Framework framework = frameworkFactory.newFramework(config);
    framework.init();

    BundleContext systemBundleContext = framework.getBundleContext();

    Artifact equinoxCompatibilityStateArtifact =
        pluginArtifactMap.get("org.eclipse.tycho:org.eclipse.osgi.compatibility.state");

    URI compatibilityBundleURI = equinoxCompatibilityStateArtifact.getFile().toURI();

    systemBundleContext.installBundle("reference:" + compatibilityBundleURI.toString());

    framework.start();

    for (String bundleLocation : bundleLocations) {
      try {
        systemBundleContext.installBundle(bundleLocation);
      } catch (BundleException e) {
        getLog().warn("Could not install bundle " + bundleLocation, e);
      }
    }
    FrameworkWiring frameworkWiring = framework
        .adapt(FrameworkWiring.class);
    frameworkWiring.resolveBundles(null);

    return framework;
  }

  /**
   * HACK to make Equinox using the classloader of the system even if LQMG is called multiple times.
   */
  private static void resetFrameworkProperties() {
    // FIXME avoid having to do this hack!!! equinox internal classes should be available via the
    // jvm classloader
    // Class<FrameworkProperties> clazz = FrameworkProperties.class;
    // try {
    // Field propertiesField = clazz.getDeclaredField("properties");
    // propertiesField.setAccessible(true);
    // propertiesField.set(null, null);
    // } catch (NoSuchFieldException e) {
    // throw new RuntimeException(e);
    // } catch (SecurityException e) {
    // throw new RuntimeException(e);
    // } catch (IllegalArgumentException e) {
    // throw new RuntimeException(e);
    // } catch (IllegalAccessException e) {
    // throw new RuntimeException(e);
    // }
  }

  private void diagnose(String[] bundleLocations) {
    Framework osgiContainer = null;
    File tempDirectory = null;
    try {
      tempDirectory = createTempDirectory();
      osgiContainer = startOSGiContainer(bundleLocations, tempDirectory.getAbsolutePath());

      printMissingRequirements(osgiContainer);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (BundleException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      if (osgiContainer != null) {
        try {
          osgiContainer.stop();
          osgiContainer.waitForStop(0);
        } catch (BundleException e) {
          getLog().error("Could not stop embedded OSGi container during code generation", e);
        } catch (InterruptedException e) {
          getLog().error("Stopping of embedded OSGi container was interrupted", e);
          Thread.currentThread().interrupt();
        }
      }
      if (tempDirectory != null) {
        deleteFolder(tempDirectory);
      }
    }
  }

  private void printMissingRequirements(Framework osgiContainer) {
    BundleContext systemBundleContext = osgiContainer.getBundleContext();

    ServiceReference<PlatformAdmin> platformServiceSR = systemBundleContext
        .getServiceReference(PlatformAdmin.class);

    PlatformAdmin platformAdmin = systemBundleContext.getService(platformServiceSR);
    State state = platformAdmin.getState();
    Bundle[] bundles = systemBundleContext.getBundles();
    List<BundleCapability> availableCapabilities = getAllCapabilities(bundles, state);

    Map<Bundle, List<ImportPackageSpecification>> requiredMissingByBundle = new TreeMap<>();
    Map<Bundle, List<ImportPackageSpecification>> optionalMissingByBundle = new TreeMap<>();

    for (Bundle bundle : bundles) {
      if (bundle.getState() == Bundle.INSTALLED) {
        List<ImportPackageSpecification> requiredMissings = new ArrayList<>();
        List<ImportPackageSpecification> optionalMissings = new ArrayList<>();

        BundleDescription bundleDescription = state.getBundle(bundle.getBundleId());

        ImportPackageSpecification[] allImports = bundleDescription.getImportPackages();

        for (ImportPackageSpecification importPackage : allImports) {
          BundleRequirement requirement = importPackage.getRequirement();
          if (!requirementSatisfiable(requirement, availableCapabilities)) {
            if (Constants.RESOLUTION_OPTIONAL
                .equals(requirement.getDirectives().get(Constants.RESOLUTION_DIRECTIVE))) {

              optionalMissings.add(importPackage);
            } else {
              requiredMissings.add(importPackage);
            }
          }
        }
        if (optionalMissings.size() > 0) {
          optionalMissingByBundle.put(bundle, optionalMissings);
        }
        if (requiredMissings.size() > 0) {
          requiredMissingByBundle.put(bundle, requiredMissings);
        }
      }
    }

    getLog().info("----- Missing required packages by bundles -----");
    Set<String> requiredSum = printBundlesWithMissingImportsAndSummarize(requiredMissingByBundle);
    getLog().info("");
    getLog().info("");
    getLog().info("----- Missing optional packages by bundles -----");
    Set<String> optionalSum = printBundlesWithMissingImportsAndSummarize(optionalMissingByBundle);
    getLog().info("");
    getLog().info("");
    getLog().info("----- Missing required packages (summary) -----");
    printMissingSummary(requiredSum);
    getLog().info("");
    getLog().info("");
    getLog().info("----- Missing optional packages (summary) -----");
    printMissingSummary(optionalSum);
    getLog().info("");
    getLog().info("");

  }

  private void printMissingSummary(Set<String> importPackages) {
    for (String importPackage : importPackages) {
      getLog().info("  Import-Package: " + importPackage);
    }
  }

  private Set<String> printBundlesWithMissingImportsAndSummarize(
      Map<Bundle, List<ImportPackageSpecification>> missingByBundle) {

    Set<String> result = new TreeSet<>();
    Set<Entry<Bundle, List<ImportPackageSpecification>>> entrySet = missingByBundle.entrySet();
    for (Entry<Bundle, List<ImportPackageSpecification>> entry : entrySet) {
      Bundle bundle = entry.getKey();
      getLog().info(bundle.getSymbolicName() + ":" + bundle.getVersion());
      List<ImportPackageSpecification> packages = entry.getValue();
      for (ImportPackageSpecification importPackage : packages) {
        getLog().info("  " + importPackage.toString());
        result.add(importPackage.getName() + ";version=" + importPackage.getVersionRange());
      }
    }

    return result;

  }

  private boolean requirementSatisfiable(final BundleRequirement requirement,
      final List<BundleCapability> availableCapabilities) {
    for (BundleCapability bundleCapability : availableCapabilities) {
      try {
        if (requirement.matches(bundleCapability)) {
          return true;
        }
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  private List<BundleCapability> getAllCapabilities(final Bundle[] bundles, final State state) {
    List<BundleCapability> availableCapabilities = new ArrayList<BundleCapability>();
    for (Bundle bundle : bundles) {
      BundleDescription bundleDescription = state.getBundle(bundle.getBundleId());
      List<BundleCapability> declaredCapabilities = bundleDescription.getDeclaredCapabilities(null);
      availableCapabilities.addAll(declaredCapabilities);
    }
    return availableCapabilities;
  }

  private static void deleteFolder(final File folder) {
    File[] files = folder.listFiles();
    if (files != null) { // some JVMs return null for empty dirs
      for (File f : files) {
        if (f.isDirectory()) {
          deleteFolder(f);
        } else {
          f.delete();
        }
      }
    }
    folder.delete();
  }

  private static File createTempDirectory() throws IOException {
    final File temp = File.createTempFile("eosgi-diagnose-",
        Long.toString(System.nanoTime()));

    if (!(temp.delete())) {
      throw new IOException("Could not delete temp file: "
          + temp.getAbsolutePath());
    }

    if (!(temp.mkdir())) {
      throw new IOException("Could not create temp directory: "
          + temp.getAbsolutePath());
    }

    return temp;
  }

}
