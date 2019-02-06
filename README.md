
Java Downloader
===============

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