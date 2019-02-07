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
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

import static java.nio.channels.FileChannel.MapMode.READ_WRITE;

class Writer implements Callable<Object>
{
    private final Logger logger = LoggerFactory.getLogger( Writer.class );

    private final BlockingQueue<PartCache> queue;

    private final RandomAccessFile randomAccessFile;

    Writer( BlockingQueue<PartCache> queue, RandomAccessFile file )
    {
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

            // TODO: Determine which is quicker ; using RandomAccessFile or a MappedByteBuffer.
            // TODO: Reference https://rick-hightower.blogspot.com/2013/11/fastet-java-io-circa-2013-writing-large.html
            // TODO: Reference https://mechanical-sympathy.blogspot.com/2011/12/java-sequential-io-performance.html

            randomAccessFile.seek( part.getIndex() );
            randomAccessFile.write( part.getCachedBytes() );

            // Another alternative is using channel write:
            // randomAccessFile.getChannel().write( ByteBuffer.wrap( part.getCachedBytes() ) );

            // randomAccessFile.getChannel().map( READ_WRITE, part.getIndex(), part.getCachedBytes().length ).put( part.getCachedBytes() );


            counter++;
        }
        // We know that there are X extractors so keep track of how many writes are done and exit when
        // its complete.
        while ( counter < JDownloader.PART_COUNT );

        logger.info( "Completed writing {} ( {} bytes )", ByteUtils.humanReadableByteCount( byteCount ), byteCount );

        return null;
    }
}
