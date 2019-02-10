
# Java Downloader


This is small library / command line program that acts as a simple axel/aria style multi-thread downloader.

It can either be used as a command line tool :

```
Usage: JDownloader [-dhV] [--out=Output] --url=URL [-p=Part-Count] [-s=Size]
Multithreaded Java JDownloader
      --out=Output   Local file
      --url=URL      Remote file url
  -d, --debug        Enable debug.
  -h, --help         Show this help message and exit.
  -p=Part-Count      Number of parts to split into (default: 8)
  -s=Size            Minimum size in bytes to multi-thread (default: 10000000). Set
                       to <= 0 to force single thread.
  -V, --version      Print version information and exit.
```

Alternatively it may be used as a library. It supports builder style composition e.g.

```
new JDownloader( <url> ).target( <file> ).execute();
```

The remote URL passed to the constructor is mandatory.

| Method | Description |
| --- | --- |
| void execute() | Computes a result, or throws an exception if unable to do so. |
| JDownloader minimumSplit(int minimumSplit) | Define the minimum split before using multi-threading. Default is 100000 (10MB). Set to <= 0 to force single threaded direct download. |
| JDownloader 	partCount(int partCount) | Defines the number of parts the remote file will be split into when using multi-threading. Defaults to number of processors. |
| JDownloader 	target(String target) | Define target file to write to. Optional, if not set, then it will default to working directory and final filename of remote.|




