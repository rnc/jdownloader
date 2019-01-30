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
import org.apache.commons.io.FilenameUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.goots.jdownloader.utils.ByteUtils;
import org.goots.jdownloader.utils.InternalException;
import org.goots.jdownloader.utils.PartCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public class JDownloader
{
    private final Logger logger = LoggerFactory.getLogger( JDownloader.class );

    final static int PART_COUNT = 20;

    private String remote;

    private String target;

    private PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();

    private LinkedBlockingQueue<PartCache> queue = new LinkedBlockingQueue<>( 20 );

    private boolean single;

    public JDownloader ( String remote )
    {
        this.remote = remote;
        cm.setDefaultMaxPerRoute( 20 );
    }

    public JDownloader target( String target )
    {
        this.target = target;
        return this;
    }

    public JDownloader single( boolean single )
    {
        this.single = single;
        return this;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @throws InternalException if unable to compute a result
     * @throws IOException if unable to compute a result
     */
    public void execute() throws InternalException, IOException
    {
        URL url = new URL( remote );
        long remoteSize = 0;

        // If target hasn't been set the default it to the filename portion of the original file
        if ( target == null )
        {
            target = FilenameUtils.getName( url.getFile() );
        }

        // Head parser
        HttpHead headMethod = new HttpHead( remote );
        boolean downloadThreaded = single;

        if ( ! downloadThreaded )
        {
            try (CloseableHttpClient httpClient = HttpClients.createDefault(); CloseableHttpResponse httpResponse = httpClient.execute( headMethod ))
            {
                if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK )
                {
                    logger.error( "The URL is not valid {} : {}", remote, httpResponse.getStatusLine().getStatusCode() );
                }

                Header acceptRange = httpResponse.getFirstHeader( HttpHeaders.ACCEPT_RANGES );

                if ( acceptRange != null && acceptRange.getValue().equals( "bytes" ) )
                {
                    logger.debug( "Header will accept range queries" );

                    Header length = httpResponse.getFirstHeader( HttpHeaders.CONTENT_LENGTH );

                    if ( length != null )
                    {
                        downloadThreaded = true;
                        remoteSize = Long.parseLong( length.getValue() );

                        logger.debug( "Length of remote is {} ({})", ByteUtils.humanReadableByteCount( remoteSize ),
                                      remoteSize );
                    }
                }
                else
                {
                    logger.debug( "Remote does not accept ranges" );
                }
            }
            headMethod.releaseConnection();
        }


        if ( downloadThreaded && remoteSize > 10000000 )
        {
            long range = remoteSize / PART_COUNT;

            CloseableHttpClient pooledClient = HttpClients.custom().setConnectionManager( cm ).build();
            // 1 extra than the number of splits for the writer thread.
            ExecutorService service = Executors.newFixedThreadPool(PART_COUNT + 1);
            HashSet<Future> parts = new HashSet<>(  );

            for ( int i=1 ; i<=PART_COUNT; i++)
            {
                parts.add( service.submit( new PartExtractor( queue, pooledClient, remote, remoteSize, range, i) ) );
            }
            // TODO: Determine if its better to write from a single thread or multiple. Logically, I would
            // TODO: think from a single with a queue.
            Future wFuture = service.submit( new Writer ( queue, target ) );

            service.shutdown();

            while ( true )
            {
                if ( wFuture.isDone() )
                {
                    break;
                }
                // no-op
            }

            for ( Future f : parts )
            {
                try
                {
                    f.get();
                }
                catch ( InterruptedException | ExecutionException e )
                {
                    if ( e.getCause() instanceof IOException )
                    {
                        throw (IOException)e.getCause();
                    }
                    else if ( e.getCause() instanceof InternalException )
                    {
                        throw (InternalException)e.getCause();
                    }
                    logger.error( "Caught exception from PartExtractor", e);
                    throw new InternalException( "Caught exception from PartExtractor", e);
                }
            }
        }
        else
        {
            logger.info ("Downloading directly to {}", target);
            FileUtils.copyURLToFile( url, new File( target ) );
        }
    }
}
