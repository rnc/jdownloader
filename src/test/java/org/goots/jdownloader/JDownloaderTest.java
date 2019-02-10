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
import org.goots.jdownloader.utils.InternalException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;

import static junit.framework.TestCase.assertTrue;

public class JDownloaderTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder(  );

    @Rule
    public final SystemOutRule systemRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void verifyContentsDirect() throws Exception
    {
        URL source = new URL("http://central.maven.org/maven2/commons-io/commons-io/2.6/commons-io-2.6.jar");

        File original = folder.newFile();
        FileUtils.copyURLToFile(source, original );

        File target = folder.newFile();

        new JDownloader( source.toString() ).target( target.getAbsolutePath() ).execute();

        assertTrue ( FileUtils.contentEquals( original, target ) );
    }

    @Test
    public void verifyContentsImplicitTargetDirect() throws Exception
    {
        URL source = new URL("http://central.maven.org/maven2/commons-io/commons-io/2.6/commons-io-2.6.jar");

        File original = folder.newFile();
        FileUtils.copyURLToFile(source, original );

        File target = new File ("commons-io-2.6.jar");

        try
        {
            new JDownloader( source.toString() ).execute();

            assertTrue( FileUtils.contentEquals( original, target ) );
        }
        finally
        {
            target.deleteOnExit();
        }
    }

    @Test
    public void verifyContents() throws Exception
    {
        URL source = new URL("http://central.maven.org/maven2/org/commonjava/maven/ext/pom-manipulation-cli/3.3.1/pom-manipulation-cli-3.3.1.jar" );

        File original = folder.newFile();
        FileUtils.copyURLToFile(source, original );

        File target = folder.newFile();

        new Main().enableDebug();
        new JDownloader(source.toString() ).target( target.getAbsolutePath() ).execute();

        assertTrue ( FileUtils.contentEquals( original, target ) );
        assertTrue( systemRule.getLog().contains( "Completed writing" ) );
    }

    @Test
    public void verifyContentsImplicitTarget() throws Exception
    {
        URL source = new URL("http://central.maven.org/maven2/org/commonjava/maven/ext/pom-manipulation-cli/3.3.1/pom-manipulation-cli-3.3.1.jar" );

        File original = folder.newFile();
        FileUtils.copyURLToFile(source, original );

        File target = new File ("pom-manipulation-cli-3.3.1.jar");

        try
        {
            new JDownloader( source.toString() ).execute();

            assertTrue( FileUtils.contentEquals( original, target ) );
            assertTrue( systemRule.getLog().contains( "Completed writing" ) );
        }
        finally
        {
            target.deleteOnExit();
        }
    }

    @Test(expected = InternalException.class )
    public void verifyErrorHandling1() throws Exception
    {
        new JDownloader( "" ).execute();
    }
}