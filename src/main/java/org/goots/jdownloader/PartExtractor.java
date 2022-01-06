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

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.goots.jdownloader.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

class PartExtractor implements Callable<Void>
{
    private final Logger logger = LoggerFactory.getLogger( PartExtractor.class );

    private final AtomicLong byteCount;

    private final int partIndex;

    private final CloseableHttpClient remoteClient;

    private final URI url;

    private final long from;

    private final long to;

    private final FileChannel channel;

    PartExtractor( String target, AtomicLong byteCount, CloseableHttpClient remoteClient, int partCount, URI url, long remoteSize, long range, int partIndex )
                    throws IOException
    {
        this.remoteClient = remoteClient;
        this.url = url;
        this.partIndex = partIndex;
        this.byteCount = byteCount;

        from = partIndex == 1 ? 0 : ( ( ( partIndex - 1 ) * range) + 1) ;
        to = partIndex == (long) partCount ?
                        // https://tools.ietf.org/html/rfc7233#page-5 range is inclusive so add 1
                        ( remoteSize + 1 ) :
                        ( partIndex * range );

        this.channel = FileChannel.open( Paths.get(target), StandardOpenOption.WRITE );

        logger.debug( "PartExtractor {} adding range from {} to {} ", partIndex, from, to );
    }

    @Override
    public Void call() throws IOException
    {
        HttpGet get = new HttpGet( url );
        get.addHeader( HttpHeaders.RANGE, "bytes=" + from + "-" + to );

        try (CloseableHttpResponse httpResponse = remoteClient.execute( get ))
        {
            if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_PARTIAL_CONTENT )
            {
                logger.error( "Did not retrieve partial content {} ", httpResponse.getStatusLine().getStatusCode() );
                throw new HttpResponseException( httpResponse.getStatusLine().getStatusCode(),
                                                 "Did not retrieve partial content; got status " + httpResponse.getStatusLine().getStatusCode()
                                                                 + " and length {}" + httpResponse.getEntity().getContentLength() );
            }

            HttpEntity entity = httpResponse.getEntity();

            logger.info( "PartExtractor {} writing via stream {} ( total : {} )",
                          partIndex,
                          ByteUtils.humanReadableByteCount( entity.getContentLength() ),
                          ByteUtils.humanReadableByteCount( byteCount.addAndGet( entity.getContentLength() ) ) );

            // Rather than converting to a byte array using EntityUtils which increases the amount of memory
            // required, convert to a stream and write using that.
            channel.transferFrom( Channels.newChannel( entity.getContent() ), from, entity.getContentLength() );
            channel.close();
        }
        catch (Throwable e )
        {
            logger.error( "Caught throwable: ", e );
            throw e;
        }
        finally
        {
            get.releaseConnection();
        }

        logger.debug ("Finished part extractor {}", partIndex);
        return null;
    }
}
