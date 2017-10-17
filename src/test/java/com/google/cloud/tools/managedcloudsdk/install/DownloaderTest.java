/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.tools.managedcloudsdk.install;

import com.google.cloud.tools.managedcloudsdk.MessageListener;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class DownloaderTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();
  @Mock private MessageListener messageListener;

  @Before
  public void setupMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testDownloadURL_createsNewDirectory() throws IOException, InterruptedException {
    Path destination = tmp.getRoot().toPath().resolve("dir-to-create").resolve("destination-file");
    Path testSourceFile = createTestRemoteResource(1);
    URL fakeRemoteResource = testSourceFile.toUri().toURL();

    Downloader downloader = new Downloader(fakeRemoteResource, destination, null, messageListener);

    Path downloaderDestination = downloader.call();
    Assert.assertEquals(destination, downloaderDestination);
  }

  @Test
  public void testDownloadURL_worksWithNullProgressListener()
      throws IOException, InterruptedException {

    Path destination = tmp.getRoot().toPath().resolve("destination-file");
    Path testSourceFile = createTestRemoteResource(1);
    URL fakeRemoteResource = testSourceFile.toUri().toURL();

    Downloader downloader = new Downloader(fakeRemoteResource, destination, null, messageListener);

    Path downloaderDestination = downloader.call();
    Assert.assertEquals(destination, downloaderDestination);
  }

  @Test
  public void testDownloadURL() throws IOException, InterruptedException {
    Path destination = tmp.getRoot().toPath().resolve("destination-file");
    long testFileSize = Downloader.BUFFER_SIZE * 10 + 1;
    Path testSourceFile = createTestRemoteResource(testFileSize);
    URL fakeRemoteResource = testSourceFile.toUri().toURL();

    Downloader downloader = new Downloader(fakeRemoteResource, destination, null, messageListener);

    Path downloaderDestination = downloader.call();
    Assert.assertEquals(destination, downloaderDestination);

    Assert.assertArrayEquals(Files.readAllBytes(destination), Files.readAllBytes(testSourceFile));

    ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

    Mockito.verify(messageListener, Mockito.atLeastOnce()).message(messageCaptor.capture());

    List<String> values = messageCaptor.getAllValues();
    Assert.assertEquals("Downloading " + fakeRemoteResource.toString(), values.get(0));
    Assert.assertEquals("Download complete", values.get(values.size() - 1));

    List<String> numericalOutput = values.subList(1, values.size() - 1);

    long lastRead = -1;
    for (String progress : numericalOutput) {
      String[] progressValues = progress.split("/");
      Assert.assertEquals(2, progressValues.length);
      long read = Long.valueOf(progressValues[0]);
      long total = Long.valueOf(progressValues[1]);
      Assert.assertTrue(read - lastRead > 0);
      lastRead = read;
      Assert.assertEquals(testFileSize, total);
    }
    Assert.assertEquals(testFileSize, lastRead);
  }

  private Path createTestRemoteResource(long sizeInBytes) throws IOException {

    Path testFile = tmp.newFile().toPath();
    try (BufferedWriter writer = Files.newBufferedWriter(testFile, Charset.defaultCharset())) {
      for (long i = 0; i < sizeInBytes; i++) {
        writer.write('a');
      }
    }
    return testFile;
  }

  @Test
  public void testDownloadURL_userAgentSet() throws IOException, InterruptedException {
    Path destination = tmp.getRoot().toPath().resolve("destination-file");

    final URLConnection mockConnection = Mockito.mock(URLConnection.class);
    URLStreamHandler testHandler =
        new URLStreamHandler() {
          @Override
          protected URLConnection openConnection(URL url) throws IOException {
            return mockConnection;
          }
        };

    // create a URL with a custom streamHandler so we can get our mock connection
    URL testUrl = new URL("", "", 80, "", testHandler);
    Downloader downloader =
        new Downloader(testUrl, destination, "test-user-agent", messageListener);

    try {
      downloader.call();
    } catch (Exception ex) {
      // ignore, we're only looking for user agent being set
    }
    Mockito.verify(mockConnection).setRequestProperty("User-Agent", "test-user-agent");
  }
}