
# Java Downloader


This is small library / command line program that acts as a simple axel/aria style multi-thread downloader.

It can either be used as a command line tool :

```
java -jar jdownloader-0.1-SNAPSHOT-jar-with-dependencies.jar --help
Usage: JDownloader [-dhsV] [--out=Output] --url=URL
Multithreaded Java JDownloader
      --out=Output   Local file
      --url=URL      Remote file url
  -d, --debug        Enable debug.
  -h, --help         Show this help message and exit.
  -s                 Force single thread
  -V, --version      Print version information and exit.
```

Alternatively it may be used as a library. It supports builder style composition e.g.

```
new JDownloader(source.toString() ).target( targetFile ).execute();
```

The target is optional ; if its not specified it will default to the current directory.


### TODO

1. Performance measurements for Writer.
    * Create an embedded server (e.g. Jetty), possibly running under JUnit.
    * Use this to test the different write methods (RandomAccessFile, ByteBuffer).
    * Also use it to verify standard download versus new method.
2. Decide on configuration for single thread / size limit.
3. Consider whether tests are needed for PartExtractor.



