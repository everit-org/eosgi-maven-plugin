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
package org.everit.osgi.dev.maven.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.everit.expression.ExpressionCompiler;
import org.everit.expression.ParserConfiguration;
import org.everit.expression.jexl.JexlExpressionCompiler;
import org.everit.osgi.dev.eosgi.dist.schema.xsd.TemplateEnginesType;
import org.everit.templating.CompiledTemplate;
import org.everit.templating.TemplateCompiler;
import org.everit.templating.html.HTMLTemplateCompiler;
import org.everit.templating.text.TextTemplateCompiler;

/**
 * This class is not thread-safe. It should be used within one thread only.
 */
public class FileManager {

  private static final int FILE_COPY_BUFFER_SIZE = 1024;

  private static final int GROUP_EXECUTE_BITMASK;

  private static final int GROUP_READ_BITMASK;

  private static final int GROUP_WRITE_BITMASK;

  private static final int OTHERS_EXECUTE_BITMASK;

  private static final int OTHERS_READ_BITMASK;

  private static final int OTHERS_WRITE_BITMASK;

  private static final int OWNER_EXECUTE_BITMASK;

  private static final int OWNER_READ_BITMASK;

  private static final int OWNER_WRITE_BITMASK;

  private static final TemplateCompiler TEMPLATE_COMPILER_HTML;

  private static final TemplateCompiler TEMPLATE_COMPILER_TEXT;

  static {
    final int octalDigitNum = 3;
    final int executeOctal = 1;
    OTHERS_EXECUTE_BITMASK = executeOctal;
    GROUP_EXECUTE_BITMASK = OTHERS_EXECUTE_BITMASK << octalDigitNum;
    OWNER_EXECUTE_BITMASK = GROUP_EXECUTE_BITMASK << octalDigitNum;

    final int writeOctal = 2;

    OTHERS_WRITE_BITMASK = writeOctal;
    GROUP_WRITE_BITMASK = OTHERS_WRITE_BITMASK << octalDigitNum;
    OWNER_WRITE_BITMASK = GROUP_WRITE_BITMASK << octalDigitNum;

    final int readOctal = 4;

    OTHERS_READ_BITMASK = readOctal;
    GROUP_READ_BITMASK = OTHERS_READ_BITMASK << octalDigitNum;
    OWNER_READ_BITMASK = GROUP_READ_BITMASK << octalDigitNum;

    ExpressionCompiler expressionCompiler = new JexlExpressionCompiler();
    TEMPLATE_COMPILER_TEXT = new TextTemplateCompiler(expressionCompiler);
    Map<String, TemplateCompiler> inlineCompilers = new HashMap<>();
    inlineCompilers.put("text", TEMPLATE_COMPILER_TEXT);
    TEMPLATE_COMPILER_HTML = new HTMLTemplateCompiler(expressionCompiler, inlineCompilers);
  }

  private static Set<PosixFilePermission> getGroupPermissions(final int unixPermissions) {
    Set<PosixFilePermission> perms = new HashSet<>();

    if ((unixPermissions & GROUP_EXECUTE_BITMASK) > 0) {
      perms.add(PosixFilePermission.GROUP_EXECUTE);
    }
    if ((unixPermissions & GROUP_READ_BITMASK) > 0) {
      perms.add(PosixFilePermission.GROUP_READ);
    }
    if ((unixPermissions & GROUP_WRITE_BITMASK) > 0) {
      perms.add(PosixFilePermission.GROUP_WRITE);
    }

    return perms;
  }

  private static Set<PosixFilePermission> getOthersPermission(final int unixPermissions) {
    Set<PosixFilePermission> perms = new HashSet<>();

    if ((unixPermissions & OTHERS_EXECUTE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OTHERS_EXECUTE);
    }
    if ((unixPermissions & OTHERS_READ_BITMASK) > 0) {
      perms.add(PosixFilePermission.OTHERS_READ);
    }
    if ((unixPermissions & OTHERS_WRITE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OTHERS_WRITE);
    }

    return perms;
  }

  private static Set<PosixFilePermission> getOwnerPerssions(final int unixPermissions) {
    Set<PosixFilePermission> perms = new HashSet<>();

    if ((unixPermissions & OWNER_EXECUTE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OWNER_EXECUTE);
    }
    if ((unixPermissions & OWNER_READ_BITMASK) > 0) {
      perms.add(PosixFilePermission.OWNER_READ);
    }
    if ((unixPermissions & OWNER_WRITE_BITMASK) > 0) {
      perms.add(PosixFilePermission.OWNER_WRITE);
    }

    return perms;
  }

  private static void setPermissionsOnFile(final File file,
      final ZipArchiveEntry entry) throws IOException {
    if (entry.getPlatform() == ZipArchiveEntry.PLATFORM_FAT) {
      return;
    }
    int unixPermissions = entry.getUnixMode();

    Set<PosixFilePermission> perms = new HashSet<>();
    perms.addAll(FileManager.getOwnerPerssions(unixPermissions));
    perms.addAll(FileManager.getGroupPermissions(unixPermissions));
    perms.addAll(FileManager.getOthersPermission(unixPermissions));

    Path path = file.toPath();
    if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(path, perms);
    } else {
      FileManager.setPermissionsOnFileInNonPosixSystem(file, perms);
    }
  }

  private static void setPermissionsOnFileInNonPosixSystem(final File file,
      final Set<PosixFilePermission> perms) {

    if (perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
      file.setExecutable(true, !perms.contains(PosixFilePermission.OTHERS_EXECUTE));
    }

    if (perms.contains(PosixFilePermission.OWNER_READ)) {
      file.setReadable(true, !perms.contains(PosixFilePermission.OTHERS_READ));
    }

    if (perms.contains(PosixFilePermission.OWNER_WRITE)) {
      file.setWritable(true, !perms.contains(PosixFilePermission.OTHERS_WRITE));
    }
  }

  private final HashSet<File> touchedFiles = new HashSet<>();

  /**
   * Copies a directory recursively or a file from the source to the target.
   */
  public void copyDirectory(final File sourceLocation, final File targetLocation)
      throws MojoExecutionException {

    if (sourceLocation.isDirectory()) {
      touchedFiles.add(targetLocation);
      if (!targetLocation.exists()) {
        targetLocation.mkdir();
      }

      String[] children = sourceLocation.list();
      for (String element : children) {
        copyDirectory(new File(sourceLocation, element), new File(targetLocation, element));
      }
    } else {
      overCopyFile(sourceLocation, targetLocation);
    }
  }

  /**
   * Returns the files that were created, modified or just touched (as their content did not change)
   * but they were not deleted afterwards.
   *
   * @return Set of touched files.
   */
  public Set<File> getTouchedFiles() {
    @SuppressWarnings("unchecked")
    Set<File> result = (Set<File>) touchedFiles.clone();
    return result;
  }

  /**
   * Copies the source file into a target file. In case the file already exists, only those bytes
   * are overwritten in the target file that are changed.
   */
  public boolean overCopyFile(final File source, final File target) throws MojoExecutionException {
    try (FileInputStream fin = new FileInputStream(source)) {
      return overCopyFile(fin, target, source.lastModified());
    } catch (IOException e) {
      throw new MojoExecutionException("Cannot copy file " + source.getAbsolutePath() + " to "
          + target.getAbsolutePath(), e);
    }
  }

  /**
   * Copies an {@link InputStream} into a file. In case the file already exists, only those bytes
   * are overwritten in the target file that are changed.
   *
   * @param is
   *          The {@link InputStream} of the source.
   * @param targetFile
   *          The file that will be overridden if it is necessary.
   * @param sourceLastModified
   *          The timestamp of the source file or entry when it was modified.
   * @return true if the target file had to be changed, false if the target file was not changed.
   * @throws IOException
   *           if there is an error during copying the file.
   */
  public boolean overCopyFile(final InputStream is, final File targetFile,
      final Long sourceLastModified) throws IOException {
    touchedFiles.add(targetFile);

    if (targetFile.exists() && sourceLastModified != null
        && targetFile.lastModified() == sourceLastModified.longValue()) {
      return false;
    }

    boolean fileChanged = false;
    long sum = 0;
    byte[] buffer = new byte[FILE_COPY_BUFFER_SIZE];

    if (!targetFile.exists()) {
      File parentFile = targetFile.getParentFile();
      parentFile.mkdirs();
      touchedFiles.add(parentFile);
      targetFile.createNewFile();
    }
    try (RandomAccessFile targetRAF = new RandomAccessFile(targetFile, "rw");) {
      long originalTargetLength = targetFile.length();
      int r = is.read(buffer);
      while (r > -1) {
        sum += r;
        byte[] bytesInTarget = tryReadingAmount(targetRAF, r);
        if (!PluginUtil.isBufferSame(buffer, r, bytesInTarget)) {
          fileChanged = true;
          targetRAF.seek(targetRAF.getFilePointer() - bytesInTarget.length);
          targetRAF.write(buffer, 0, r);
        }

        r = is.read(buffer);
      }
      if (sum < originalTargetLength) {
        targetRAF.setLength(sum);
      }
    }
    if (sourceLastModified != null) {
      targetFile.setLastModified(sourceLastModified);
    }
    return fileChanged;
  }

  /**
   * Replaces the original template file with parsed and processed file.
   */
  public void replaceFileWithParsed(final File parseableFile, final Map<String, Object> vars,
      final String encoding, final TemplateEnginesType templateEngine)
      throws IOException, MojoExecutionException {

    File tmpFile = File.createTempFile("eosgi-dist-parse", "tmp");
    ClassLoader cl = this.getClass().getClassLoader();
    ParserConfiguration configuration = new ParserConfiguration(cl);
    configuration.setName(parseableFile.getPath());

    try (FileInputStream fin = new FileInputStream(parseableFile);
        FileOutputStream fout = new FileOutputStream(tmpFile);
        OutputStreamWriter writer = new OutputStreamWriter(fout)) {

      String templateText = IOUtils.toString(fin);
      CompiledTemplate compiledTemplate;
      if (TemplateEnginesType.TEXT.equals(templateEngine)) {
        compiledTemplate = TEMPLATE_COMPILER_TEXT.compile(templateText, configuration);
      } else {
        compiledTemplate = TEMPLATE_COMPILER_HTML.compile(templateText, configuration);
      }

      compiledTemplate.render(writer, vars);
    }

    overCopyFile(tmpFile, parseableFile);
    tmpFile.delete();
  }

  /**
   * Reads the given amount of bytes from the the {@link RandomAccessFile}.
   */
  public byte[] tryReadingAmount(final RandomAccessFile is, final int amount) throws IOException {
    ByteArrayOutputStream bout = new ByteArrayOutputStream(amount);
    byte[] buffer = new byte[amount];
    int r = is.read(buffer);
    while ((r > -1) && (bout.size() < amount)) {
      bout.write(buffer, 0, r);
      r = is.read(buffer, 0, amount - bout.size());
    }
    return bout.toByteArray();
  }

  /**
   * Unpacks a ZIP file to the destination directory.
   *
   * @throws MojoExecutionException
   *           if something goes wrong during unpacking the files.
   */
  public void unpackZipFile(final File file, final File destinationDirectory)
      throws MojoExecutionException {

    try (ZipFile zipFile = new ZipFile(file)) {
      Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement();
        String name = entry.getName();
        File destFile = new File(destinationDirectory, name);
        if (entry.isDirectory()) {
          touchedFiles.add(destFile);
          destFile.mkdirs();
        } else {
          File parentFolder = destFile.getParentFile();
          parentFolder.mkdirs();
          InputStream inputStream = zipFile.getInputStream(entry);
          overCopyFile(inputStream, destFile, entry.getLastModifiedDate().getTime());
          FileManager.setPermissionsOnFile(destFile, entry);
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Could not uncompress distribution package file "
          + file.getAbsolutePath() + " to target folder " + destinationDirectory.getAbsolutePath(),
          e);
    }
  }
}
