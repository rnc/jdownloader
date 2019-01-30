/*
 * Copyright (C) 2019 Red Hat, Inc.
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

package org.goots.jdownloader;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;

import static junit.framework.TestCase.assertTrue;

public class JDownloaderTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder(  );

    @Test
    public void verifyContents() throws Exception
    {
        URL commonsio = new URL("http://central.maven.org/maven2/commons-io/commons-io/2.6/commons-io-2.6.jar");

        File original = folder.newFile();
        FileUtils.copyURLToFile(commonsio, original );

        File newdownload = folder.newFile();

        new JDownloader(commonsio.toString() ).target( newdownload.getAbsolutePath() ).execute();

        assertTrue ( FileUtils.contentEquals( original, newdownload ) );
    }

}