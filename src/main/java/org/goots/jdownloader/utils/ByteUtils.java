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

package org.goots.jdownloader.utils;

public class ByteUtils
{
    // https://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java/3758880#3758880
    public static String humanReadableByteCount( long bytes )
    {
        int unit = 1000;
        if ( bytes < unit )
        {
            return bytes + " B";
        }
        int exp = (int) ( Math.log( bytes ) / Math.log( unit ) );
        String pre = ( "kMGTPE" ).substring( exp - 1, exp );
        return String.format( "%.1f %sB", bytes / Math.pow( unit, exp ), pre );
    }
}
