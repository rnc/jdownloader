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

import org.goots.jdownloader.utils.InternalException;
import org.goots.jdownloader.utils.PartCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

class Writer implements Callable<Object>
{
    private final Logger logger = LoggerFactory.getLogger( Writer.class );

    private final LinkedBlockingQueue<PartCache> queue;

    private final File file;

    Writer( LinkedBlockingQueue<PartCache> queue, String target )
    {
        this.queue = queue;
        this.file = new File( target);
    }

    @Override
    public Object call() throws IOException, InternalException
    {
        RandomAccessFile randomAccessFile = new RandomAccessFile( file, "rw" );
        int counter = 0;
        long byteCount = 0;

        do
        {
            PartCache part;
            try
            {
                part = queue.take();
            }
            catch ( InterruptedException e )
            {
                throw new InternalException( "Interrupted while waiting for queue", e );
            }

            logger.debug( "Writing {} bytes ( total : {} bytes )", part.getCachedBytes().length,
                          ( byteCount += part.getCachedBytes().length) );
            randomAccessFile.seek( part.getIndex() );
            randomAccessFile.write( part.getCachedBytes() );
            counter++;

            logger.debug( "Finished writing {} bytes to {} ( counter : {} )",
                         part.getCachedBytes().length, file, counter );
        }
        // We know that there are X extractors so keep track of how many writes are done and exit when
        // its complete.
        while ( counter < JDownloader.PART_COUNT );

        logger.info( "Completed writing {} bytes", byteCount );

        return null;
    }
}
