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
import org.goots.jdownloader.utils.InternalRuntimeException;
import org.goots.jdownloader.utils.PartCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

public class JDownloader
{
    // Default of 10MB
    static final int SPLIT_DEFAULT = 10000000;

    private final Logger logger = LoggerFactory.getLogger( JDownloader.class );

    private int partCount = Runtime.getRuntime().availableProcessors() < 4 ? 4 : Runtime.getRuntime().availableProcessors();

    private URL remote;
 
    private String target;

    private PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();

    private BlockingQueue<PartCache> queue = new LinkedBlockingQueue<>( partCount );

    private int minimumSplit = SPLIT_DEFAULT;

    public JDownloader ( String remote ) throws InternalException, IOException
    {
        if ( remote == null || remote.length() == 0 )
        {
            throw new InternalException( "No remote specified" );
        }

        this.remote = new URL ( remote );

        // Slightly smaller connection pool than the split count ; this ensures we don't
        // cache the entire file in memory at once but stagger it slightly.
        cm.setDefaultMaxPerRoute( partCount - (int)( partCount * 0.15 ) );
        cm.setMaxTotal( partCount * 10 );
    }

    public JDownloader ( URL remote ) throws InternalException
    {
        if ( remote == null )
        {
            throw new InternalException( "No remote specified" );
        }

        this.remote = remote;

        // Slightly smaller connection pool than the split count ; this ensures we don't
        // cache the entire file in memory at once but stagger it slightly.
        cm.setDefaultMaxPerRoute( partCount - (int)( partCount * 0.15 ) );
        cm.setMaxTotal( partCount * 10 );
    }

    /**
     * Define target file to write to. Optional, if not set, then it will default to
     * working directory and final filename of remote.
     * @param target the target filename.
     * @return this object.
     */
    public JDownloader target( String target )
    {
        this.target = target;

        return this;
    }

    /**
     * Define the minimum split before using multi-threading. Default is 100000 (10MB).
     * Set to &lt;= 0 to force single threaded direct download.
     * @param minimumSplit the split in bytes
     * @return this object
     */
    public JDownloader minimumSplit( int minimumSplit )
    {
        this.minimumSplit = minimumSplit;
        return this;
    }

    /**
     * Defines the number of parts the remote file will be split into when using multi-threading.
     * Defaults to number of processors.
     * @param partCount number of parts.
     * @return this object
     */
    public JDownloader partCount( int partCount )
    {
        this.partCount = partCount;
        return this;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @throws InternalException if unable to compute a result
     * @throws IOException if unable to compute a result
     * @throws URISyntaxException if unable to compute a result
     */
    public void execute() throws InternalException, IOException, URISyntaxException
    {
        long remoteSize = 0;
        RandomAccessFile targetFile;

        // If target hasn't been set the default it to the filename portion of the original file
        if ( target == null || target.length() == 0 )
        {
            target = FilenameUtils.getName( remote.getFile() );
        }

        try ( CloseableHttpClient pooledClient = HttpClients.custom().setConnectionManager( cm ).build() )
        {
            boolean downloadThreaded = false;
            final URI remoteURI = remote.toURI();

            if ( minimumSplit > 0 )
            {
                // Head parser
                HttpHead headMethod = new HttpHead( remoteURI );

                try ( CloseableHttpResponse httpResponse = pooledClient.execute( headMethod ) )
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

                            if ( logger.isDebugEnabled() )
                            {
                                logger.debug( "Length of remote is {} ({})", ByteUtils.humanReadableByteCount( remoteSize ),
                                              remoteSize );
                            }
                        }
                        else
                        {
                            logger.error( "Remote did not specify a range" );
                        }
                    }
                    else
                    {
                        logger.error( "Remote does not accept ranges" );
                    }
                }
            }

            if ( downloadThreaded && remoteSize > minimumSplit )
            {
                long range = remoteSize / partCount;

                // 1 extra than the number of splits for the writer thread.
                ExecutorService service = Executors.newFixedThreadPool( partCount + 1 );
                HashSet<Future<PartCache>> parts = new HashSet<>();

                targetFile = new RandomAccessFile( target, "rw" );
                // Pre-allocate the length to avoid repeated resize.
                targetFile.setLength( remoteSize );

                for ( int i = 1; i <= partCount; i++ )
                {
                    parts.add( service.submit( new PartExtractor( pooledClient, partCount, remoteURI, remoteSize, range, i ) ) );
                }
                Future wFuture = service.submit( new Writer( partCount, queue, targetFile ) );

                try
                {
                    parts.parallelStream().forEach( p -> {
                        try
                        {
                            queue.put( p.get() );
                        }
                        catch ( InterruptedException | ExecutionException e )
                        {
                            throw new InternalRuntimeException( "Stream error", e );
                        }
                    } );
                }
                catch ( InternalRuntimeException e )
                {
                    if ( e.getCause() instanceof IOException )
                    {
                        throw (IOException) e.getCause();
                    }
                    else if ( e.getCause() instanceof InternalException )
                    {
                        throw (InternalException) e.getCause();
                    }
                    else if ( e.getCause() instanceof OutOfMemoryError )
                    {
                        logger.error( "Caught OutOfMemory exception from PartExtractor", e );

                        // Terminate as fatal error.
                        service.shutdownNow();

                        throw new InternalException( "Fatal out of memory", e );
                    }
                    else
                    {
                        logger.error( "Caught exception from PartExtractor", e );
                        throw new InternalException( "Caught exception from PartExtractor", e );
                    }
                }

                while ( true )
                {
                    // no-op
                    if ( wFuture.isDone() )
                    {
                        break;
                    }
                }

                service.shutdown();
                targetFile.close();
            }
            else
            {
                logger.info( "Downloading directly to {}", target );
                File fTarget = new File( target );
                FileUtils.copyURLToFile( remote, fTarget );

                logger.info( "Completed writing {} ( {} bytes )", ByteUtils.humanReadableByteCount( fTarget.length() ),
                             fTarget.length() );
            }
        }
    }
}
