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
import org.goots.jdownloader.utils.ManifestVersionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

import static org.goots.jdownloader.JDownloader.SPLIT_DEFAULT;

@CommandLine.Command( name = "JDownloader",
                      description = "Multithreaded Java JDownloader",
                      versionProvider = ManifestVersionProvider.class,
                      mixinStandardHelpOptions = true// add --help and --version options
                      )
public class Main implements Callable<Void>
{
    @Option( names = { "-d", "--debug" }, description = "Enable debug." )
    private boolean debug;

    @Option( names = { "--url" }, required = true, paramLabel = "URL", description = "Remote file url" )
    private String remote;

    @Option( names = { "--out" }, paramLabel = "Output", description = "Local file" )
    private String target;

    @Option( names = { "-p" }, paramLabel = "Part-Count", description = "Number of parts to split into (default: ${DEFAULT-VALUE})")
    private int partCount = Runtime.getRuntime().availableProcessors() < 4 ? 4 : Runtime.getRuntime().availableProcessors();

    @Option( names = { "-s" }, paramLabel = "Size", description = "Minimum size in bytes to multi-thread (default: ${DEFAULT-VALUE}). Set to <= 0 to force single thread." )
    private int minimumSplit = SPLIT_DEFAULT;

    public static void main( String[] args ) throws Exception
    {
        final ExceptionHandler<List<Object>> handler = new ExceptionHandler<>();
        try
        {
            CommandLine cl = new CommandLine( new Main() );
            cl.parseWithHandlers( new CommandLine.RunAll(), handler, args );
        }
        catch ( CommandLine.ExecutionException e )
        {
            if ( e.getCause() != null && e.getCause() instanceof Exception )
            {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    @Override
    public Void call() throws Exception
    {
        if ( debug )
        {
            enableDebug();
        }

        new JDownloader( remote ).target( target ).partCount( partCount ).minimumSplit( minimumSplit ).execute();

        return null;
    }

    void enableDebug()
    {
        ch.qos.logback.classic.Logger rootLogger =
                        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );

        rootLogger.setLevel( Level.DEBUG );
    }

    private static class ExceptionHandler<R>
                    extends CommandLine.DefaultExceptionHandler<R>
    {
        /**
         * Prints the message of the specified exception, followed by the usage message for the command or subcommand
         * whose input was invalid, to the stream returned by {@link #err()}.
         * @param ex the ParameterException describing the problem that occurred while parsing the command line arguments,
         *           and the CommandLine representing the command or subcommand whose input was invalid
         * @param args the command line arguments that could not be parsed
         * @return the empty list
         * @since 3.0 */
        public R handleParseException( CommandLine.ParameterException ex, String[] args )
        {
            super.handleParseException( ex, args );
            andExit( 1 );
            return returnResultOrExit( null );
        }
    }
}
