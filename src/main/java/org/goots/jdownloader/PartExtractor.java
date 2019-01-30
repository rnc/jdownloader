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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.CloseableHttpClient;
import org.goots.jdownloader.utils.InternalException;
import org.goots.jdownloader.utils.PartCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

class PartExtractor implements Callable<Object>
{
    private final Logger logger = LoggerFactory.getLogger( PartExtractor.class );

    private final long remoteSize;

    private final long range;

    private final int partIndex;

    private final CloseableHttpClient remoteClient;

    private final String url;

    private final LinkedBlockingQueue<PartCache> queue;

    PartExtractor( LinkedBlockingQueue<PartCache> queue, CloseableHttpClient remoteClient, String url,
                   long remoteSize, long range, int partIndex )
    {
        this.remoteClient = remoteClient;
        this.url = url;
        this.remoteSize = remoteSize;
        this.range = range;
        this.partIndex = partIndex;
        this.queue = queue;
    }

    @Override
    public Object call() throws IOException, InternalException
    {
        String from = partIndex == 1 ? "0" : Long.toString( ( ( partIndex - 1 ) * range) + 1) ;
        String to = partIndex == JDownloader.PART_COUNT ?
                        // https://tools.ietf.org/html/rfc7233#page-5 range is inclusive so add 1
                        Long.toString( remoteSize + 1 ) :
                        Long.toString( partIndex * range );
        logger.debug( "Adding range for index {} from {} to {} ", partIndex, from, to );

        HttpGet get = new HttpGet( url );
        get.addHeader( HttpHeaders.RANGE, "bytes=" + from + "-" + to );

        try (CloseableHttpResponse httpResponse = remoteClient.execute( get, HttpClientContext.create() ) )
        {
            if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_PARTIAL_CONTENT )
            {
                throw new InternalException( "Did not retrieve partial content; got status " +
                                                             httpResponse.getStatusLine().getStatusCode() +
                                                             " and length {}" +
                                                            httpResponse.getEntity().getContentLength() );
            }
            logger.debug( "### Part {} retrieved {}", partIndex, httpResponse.getEntity().getContentLength() );

            // TODO: Determine if its better to write from a single thread or multiple. Logically, I would
            // TODO: think from a single with a queue.
            queue.add( new PartCache( IOUtils.toByteArray( httpResponse.getEntity().getContent() ), Long.parseLong( from ) ) );
/*
            RandomAccessFile randomAccessFile = new RandomAccessFile( file, "rw" );
            randomAccessFile.seek( Long.parseLong( from ) );

            logger.info( "### Part {} writing {}", partIndex, httpResponse.getEntity().getContentLength() );

            randomAccessFile.getChannel().transferFrom
                             ( Channels.newChannel(
                                             httpResponse.getEntity().getContent() ),
                               0 , httpResponse.getEntity().getContentLength()  );
             randomAccessFile.close();
*/
            logger.debug ("### Completed copy to queue for {}", partIndex);
        }

        get.releaseConnection();

        logger.debug ("Finished part extractor {}", partIndex);

        return null;
    }
}
