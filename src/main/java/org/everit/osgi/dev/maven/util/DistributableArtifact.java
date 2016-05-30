package org.everit.osgi.dev.maven.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * An artifact that can be copied to the distributed environment.
 */
public class DistributableArtifact {

  public String downloadURL;

  public File file;

  public String gav;

  public Map<String, String> properties = new HashMap<>();

  public String targetFile;

  public String targetFolder;
}
