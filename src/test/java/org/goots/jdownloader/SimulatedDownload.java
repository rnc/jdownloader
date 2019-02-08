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

import ch.qos.logback.classic.Level;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertTrue;

// Note : unless this is named SimulatedDownloadTest it won't be picked up by Maven.
public class SimulatedDownload
{
    private static final Logger logger = LoggerFactory.getLogger( SimulatedDownload.class );

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder(  );

    @Rule
    public final SystemOutRule systemRule = new SystemOutRule().enableLog(); //.muteForSuccessfulTests();

    private static Server server;

    @Before
    public void before() throws Exception
    {
//        ExecutorThreadPool executorThreadPool = new ExecutorThreadPool( Executors.newFixedThreadPool( 500));
        server = new Server();
        ServerConnector connector = new ServerConnector( server );
        connector.setPort( 8080 );
        server.addConnector( connector );

        URL source = new URL( "http://central.maven.org/maven2/org/commonjava/maven/ext/pom-manipulation-cli/3.3.1/pom-manipulation-cli-3.3.1.jar" );
        File original = folder.newFile();
        FileUtils.copyURLToFile( source, original );

        // Setup the basic application "context" for this application at "/"
        // This is also known as the handler tree (in jetty speak)
        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
        context.setResourceBase( System.getProperty( "user.dir" ) + "/rhdm-7.2.0-container-sources.zip" );
//        context.setResourceBase( original.getAbsolutePath() );
        context.setContextPath( "/" );
        server.setHandler( context );

        ServletHolder holderPwd = new ServletHolder( "default", DefaultServlet.class );
        holderPwd.setInitParameter( "dirAllowed", "true" );
        context.addServlet( holderPwd, "/" );

        server.start();
    }

    @After
    public void after() throws Exception
    {
        server.stop();
    }


    @Test
    public void verifyContents() throws Exception
    {
        ch.qos.logback.classic.Logger rootLogger =
                        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );

        rootLogger.setLevel( Level.INFO );
        try
        {
            final URL source = server.getURI().toURL();
            final File target = folder.newFile();
            final int COUNT = 1;
            final HashSet<Long> set = new HashSet<>();

            logger.warn( "Attempt to download from {} ", source );

            for ( int i = 0; i < COUNT; i++ )
            {
                long start = System.nanoTime();
                new JDownloader( source.toString() ).single( true ).target( target.getAbsolutePath() ).execute();
                set.add( System.nanoTime() - start );
            }

            long directResult = TimeUnit.NANOSECONDS.toMillis( set.stream().mapToLong( Long::longValue ).sum() / COUNT );
            logger.warn( "Single took {} milliseconds with set {} ", directResult, set );

            set.clear();

            for ( int i = 0; i < COUNT; i++ )
            {
                long start = System.nanoTime();
                new JDownloader( source.toString() ).target( target.getAbsolutePath() ).execute();
                set.add( System.nanoTime() - start );
            }

            long result = TimeUnit.NANOSECONDS.toMillis( set.stream().mapToLong( Long::longValue ).sum() / COUNT );
            logger.warn( "Multithreaded took {} milliseconds with set {} ", result, set );

            assertTrue( result < directResult );

        }
        finally
        {
            rootLogger.setLevel( Level.INFO );
        }
    }
}