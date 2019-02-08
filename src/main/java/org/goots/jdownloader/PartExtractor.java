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

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.goots.jdownloader.utils.PartCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;

class PartExtractor implements Callable<PartCache>
{
    private final Logger logger = LoggerFactory.getLogger( PartExtractor.class );

    private final int partIndex;

    private final CloseableHttpClient remoteClient;

    private final String url;

    private long from;

    private long to;

    PartExtractor( CloseableHttpClient remoteClient, String url, long remoteSize, long range, int partIndex )
    {
        this.remoteClient = remoteClient;
        this.url = url;
        this.partIndex = partIndex;

        from = partIndex == 1 ? 0 : ( ( ( partIndex - 1 ) * range) + 1) ;
        to = partIndex == (long) JDownloader.PART_COUNT ?
                        // https://tools.ietf.org/html/rfc7233#page-5 range is inclusive so add 1
                        ( remoteSize + 1 ) :
                        ( partIndex * range );
        logger.debug( "PartExtractor {} adding range from {} to {} ", partIndex, from, to );
    }

    @Override
    public PartCache call() throws IOException
    {
        HttpGet get = new HttpGet( url );
        get.addHeader( HttpHeaders.RANGE, "bytes=" + from + "-" + to );
        PartCache result ;

        try
        {
            result = remoteClient.execute( get, httpResponse -> {
                if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_PARTIAL_CONTENT )
                {
                    logger.error( "Did not retrieve partial content {} ", httpResponse.getStatusLine().getStatusCode() );
                    throw new HttpResponseException( httpResponse.getStatusLine().getStatusCode(),
                                                     "Did not retrieve partial content; got status " + httpResponse.getStatusLine().getStatusCode()
                                                                     + " and length {}" + httpResponse.getEntity().getContentLength() );
                }

                logger.debug( "### Part {} retrieved {} ", partIndex, httpResponse.getEntity().getContentLength() );

                return new PartCache( EntityUtils.toByteArray( httpResponse.getEntity() ), from );
            } );
        }
        catch (Throwable e )
        {
            logger.error( "Caught throwable: ", e );
            throw e;
        }
        finally
        {
            logger.debug( "### Release connection" );

            get.releaseConnection();
        }

        logger.debug ("Finished part extractor {}", partIndex);

        return result;
    }
}
