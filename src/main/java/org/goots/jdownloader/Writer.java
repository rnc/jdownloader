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

import org.goots.jdownloader.utils.ByteUtils;
import org.goots.jdownloader.utils.PartCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

class Writer implements Callable<Object>
{
    private final Logger logger = LoggerFactory.getLogger( Writer.class );

    private final BlockingQueue<PartCache> queue;

    private final RandomAccessFile randomAccessFile;

    private final int partCount;

    Writer( int partCount, BlockingQueue<PartCache> queue, RandomAccessFile file )
    {
        this.partCount = partCount;
        this.queue = queue;
        this.randomAccessFile = file;
    }

    @Override
    public Object call() throws IOException, InterruptedException
    {
        int counter = 0;
        long byteCount = 0;

        do
        {
            PartCache part = queue.take();

            byteCount += part.getCachedBytes().length;

            logger.debug( "Writing {} bytes ( total : {} bytes ) queue {} ", part.getCachedBytes().length,
                          byteCount, queue.size() );

            randomAccessFile.seek( part.getIndex() );
            // Options for writing to file...
            // randomAccessFile.write( part.getCachedBytes() );
            // Another alternative is using channel write:
            // randomAccessFile.getChannel().write( ByteBuffer.wrap( part.getCachedBytes() ) );

            randomAccessFile.getChannel().map( FileChannel.MapMode.READ_WRITE, part.getIndex(), part.getCachedBytes().length ).put( part.getCachedBytes() );

            counter++;
        }
        // We know that there are X extractors so keep track of how many writes are done and exit when
        // its complete.
        while ( counter < partCount );

        logger.info( "Completed writing {} ( {} bytes )", ByteUtils.humanReadableByteCount( byteCount ), byteCount );

        return null;
    }
}
