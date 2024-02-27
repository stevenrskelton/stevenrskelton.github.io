---
title: "Using Http4s Streams and Backpressure to Reduce Multipart File Upload Data Copy"
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
falls into this category, their performance benefits often passed-over until they are absolutely required by software
to function correctly.

# HTTP File Transfers

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

A common scenario where streaming is required, rather than optional, is handling file uploads. An typical example
encountered is to fully buffer a file upload within a proxy. In the following example, the user is allowed to upload
a file within the Salesforce UI, with the destination of the file to be stored in SharePoint.

{%
include figure image_path="/assets/images/2024/02/salesforce-upload.svg"
caption="File Upload from Salesforce to SharePoint"
img_style="padding: 0 20px 10px 20px; background-color: white; height: 250px;"
%}

There are 3 different approaches to this:

* File is uploaded to Salesforce, then copied to SharePoint
* File is buffered in Salesforce memory, then copied to SharePoint
* File streams to SharePoint, either directly or proxies through Salesforce servers

The first 2 are without streaming and suffer from transfers `A` and `B` being sequential rather than concurrent. The
user must wait for `B` after `A` has completed. This also allows new modes of failure: what happens when `B` fails,
will `A` have to be repeated? Will the user know `B` will fail before starting `A`?

Not only is the streaming solution faster, it has fewer modes of failure.

### Backpressure

The idea of backpressure is that consumers can dictate throughput to the producer. The consumer essentially applies
pressure backwards against the path of flow towards the producer. Streams have different implementations and not all
streams allow for backpressure. Naive buffering can prevent backpressure signals from traveling back towards the source.
But when buffering is limited or non-existent, if the consumer is slower than the producer then there is nowhere for the
data to build up. For file transfers, the backpressure solution is obvious such that the producer isn't allowed to
upload faster than the consumer can process.

This is ideal for file transfers because we want the outcome of the consumer to be relayed to the producer. If the file
upload to SharePoint `B` doesn't complete, we don't benefit from `A` completing successfully.

## Content-Range Requests

The most common approach is transmit data using the HTTP request body. When helpful, the `Content-Type` header can be
used to indicate the data type within the body to aid in parsing, but generally file uploads will be treated as binary.
The [`Content-Range`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Range) header is used when
supported by the server to indicate the segment of file being transmitted by a specific request, allowing for file
resumption, concurrent uploads, and for the client to more easily track the upload progress.

## HTTP Multipart Requests

A variation to using the HTTP request body to transmit data is the multipart request. As indicated by its `Content-Type`
header `multipart/form-data` the primary use-case is directly encoding multiple form fields using the HTTP
specification without dependencies on external encoding such as JSON. The multipart mechanism gives each part 
its own headers and body, allowing mixed use flexibility for text or binary data.

{%
include figure image_path="/assets/images/2024/02/multipart-request.svg"
caption="Multipart HTTP Request with 2 parts"
img_style="padding: 10px; background-color: white; height: 600px;"
%}

From a high level, the HTTP request is broken up by an arbitrary, unique text sequence `boundary` which needs to be
included in the `Content-Type` header. The `boundary` marks the boundary between each part in the multipart request.

A primary failure of this is that there is no indication to the number or content of each part within the HTTP headers,
meaning that each part cannot be processed until prior parts have been read. This has consequences on how effectively
multipart requests can be used for streaming data and file uploads.

# Http4s EntityDecoder

The `org.http4s.EntityDecoder` trait includes the documentation:

> A type that can be used to decode a Message EntityDecoder is used to attempt to decode a Message returning the entire
> resulting A. If an error occurs it will result in a failed effect. The default decoders provided here are not
> streaming,
> but one could implement a streaming decoder by having the value of A be some kind of streaming construct.

It is also readily apparent from the `org.http4s.multipart.Multipart` implementation:

```scala
final case class Multipart[+F[_]](
                                   parts: Vector[Part[F]],
                                   boundary: Boundary,
                                 )
```

As mentioned earlier, the parts of a multipart request are sequential, and `Vector` is an immutable collection with a
definite size. This means that this `Multipart` implementation requires knowledge of the entire request to be
constructed. This implementation is incompatible with streaming applications.

Out-Of-Memory issues are circumvented by way of temporary files, but this will have implications on throughput even when
using SSDs. The implicits in scope will use default values, but it is possible to change default values by directly
creating the decoder using the `MultipartDecoder` static method `mixedMultipartResource`:

```scala
def mixedMultipartResource[F[_] : Concurrent : Files](
                                                       headerLimit: Int = 1024,
                                                       maxSizeBeforeWrite: Int = 52428800,
                                                       maxParts: Int = 50,
                                                       failOnLimit: Boolean = false,
                                                       chunkSize: Int = 8192,
                                                     ): Resource[F, EntityDecoder[F, Multipart[F]]]
``` 

# Streaming File Uploads with Http4s

//TODO: implementation details for streaming Multipart decoder

