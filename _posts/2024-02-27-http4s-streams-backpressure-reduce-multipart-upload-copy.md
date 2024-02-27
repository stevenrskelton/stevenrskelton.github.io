---
title: "Using Http4s Streams and Back-pressure to Reduce Multipart File Upload Data Copy"
categories:
  - Scala
tags:
  - Typelevel/Cats
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
examples:
  - http-maven-receiver
---

Streaming is the primary mechanism to reduce memory requirements of large datasets. The approach is to never requiring 
the entire dataset be in memory at once but rather allow the data to stream through in manageable amounts, much like 
water in a river, allowing large or infinite amounts to be processed over time. A practical example is a file-upload,
written using streams multi-GBs files can be transferred using minimal heap/RAM, however an incomplete implementation 
will cause OOM errors if it were to try and fit the entire file into memory at once.<!--more-->

{% include table-of-contents.html height="300px" %}

# Collections versus Streams

Pragmatic programming moved complexity from projects to external libraries and frameworks. This allowed even small 
improvements in libraries to have over-sized benefit scaling proportionally to their popularity. However, this has the 
unintended consequence of developers never learning underlying concepts, both limiting their work within project 
implementations directly and through misusing or under-using available libraries. From personal observation, streams 
falls into this category, their performance benefits often passed-over until it is absolutely required to function.

# File Transfers

It is inappropriate to write ETLs assuming the entire dataset will fit into RAM. The OS will attempt to manage if given
enough virtual memory, but it will be blind to program implementation details and unable to perform optimally. Beyond 
this, by not streaming data directly from source to destination the additional steps can inefficiently be required to
copy data multiple times to different locations, noticeably decreasing throughput based on available I/O.

## Middlemen and Proxies

When data is transferred from source to destination, any proxies buffering substantial amounts of data can cause 
performance degradation if using the wrong implementation. Proxies should use streams when possible, unless the 
buffering is serving an explicit purpose, such as increasing data durability, smoothing irregular network flow or 
providing batch/windowing.

### File Uploads without Streams

A common scenario where streaming is required, rather than optional, is handling file uploads.


. . Efficient proxies will act as a router holding on to the smallest amount of data possible, and 
copying this data internally the least number of times.  Buffering is . This
could range from the scale of TCP network packets

It might
be technically possible to delegate responsibility to the OS, forcing it to page virtual memory, 
memory can be used

the requirement to ha require sufficient RAM tDatasets have grown in size much faster than RAM, to the , and even simple ETL processes can encounter 

## Spring Java Repositories

The Spring framework on the JVM has expanded its repository pattern to expose `java.util.stream` datasets along-side 
the convention `List` collections. 
for requiring additional dependencies, appearing less frequently in example code, alternative interfaces to be used
which

understanding becoming overly pragThis can have the unintented
consequence of This is the most
efficient Many projects benefit
when libraries improve, while the scope of individual projects is improvements in a way that librariThis raises
efficiency
since libraries are widely consumed



## Developer Experience



### Debugging Read-Once

## Memory Use and Data Copying

### JVM Heap requirements for a File Upload

# HTTP Multipart Request

## Format and Boundaries

## File Uploads

# Http4s EntityDecoder

