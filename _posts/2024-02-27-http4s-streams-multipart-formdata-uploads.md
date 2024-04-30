---
title: "Http4s Streams and Multipart Form-Data File Uploads"
categories:
  - Scala
tags:
  - Typelevel/Cats
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
example: http-maven-receiver
---

Streaming is the primary mechanism to reduce memory requirements for processing large datasets. The approach is to 
view only a small window of data at a time, allowing data to stream through in manageable amounts matching the 
data window size to the amount of RAM available. A practical example is a file-upload, where multi-GBs file streams 
can be handled by MBs of server RAM. However, enforcing streaming in software code is prone to errors, and misuse or
incompatible method implementations will lead to breaking stream semantics, and ultimately to OOM exceptions. This 
article focuses on streams within the context of file uploads, using the Http4s library for examples.<!--more-->

{% include table-of-contents.html height="600px" %}

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

## Intermediate Nodes and Proxies

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

## Strategies for HTTP File Transfers

### Large File Transfers

Large file transfers have additional concerns over smaller files:

* Ability to track progress,
* Ability to pause/resume over difference sessions,
* Ability to parallelize,
* Ability to support unknown or unlimited final file size

#### Content-Range Requests

The simplest strategy is to stream file contents within a single HTTP request body. A _chunked transfer
encoding_ can be specifying using a `Transfer-Encoding: chunked` header allowing unknown or unlimited request size,
however the request has limited recoverability since there is no mechanism to pause, resume or parallelize for
performance. The `Content-Range` header is a common approach to expand into these features by replacing a single large
request with multiple smaller requests. Each request will specify a byte range to be transmitted, allowing the requests
to be parallelized, retried, and monitored.

#### Alternative SDK Approaches

While the `Content-Range` approach is a common standard built into all browsers, alternatives exist such as within the
[AWS SDK](https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html). The AWS SDK builds out additional file
features:

* Ability to list complete/incomplete segments
* Management of in-progress transfers
* Support a file lifecycle

AWS has chosen not to use content-range headers, instead utilizing a custom part identification scheme. Each request
will include the part identifier within the URL query parameters instead of within a header.

Not covered within the content-range approach is how to manage in-progress transfers. Are each range tied to a specific
HTTP session? Can they be deleted? How can we list incomplete or range failures? By exposing additional URLs to list
transfers in progress, list transfer part statuses, and manage/delete transfers, AWS has both increased the durability
of the file transfer process and removed state management complexity from client agents.

The final feature of a file lifecycle exists to mark transfers as complete, such that they can be transferred from
temporary locations used during the upload to final destinations. This is important because of the granularity of
destinations, some being referred to as cold or offline storage, which would normally not be directly accessible to
clients.

### HTTP Multipart Requests

A variation to a singular HTTP request body is the multipart request. It is indicated by way of
a `Content-Type: multipart/form-data` header. As can be inferred from the form-data name, the primary use-case was the
expansion of the HTTP specification to directly support form data transfers without dependency on external encoding
schema such as JSON.

The multipart mechanism separates the request body into separate parts, each with its own headers and body. Each of the
parts is free to specify their _Content-Type_ thereby allowing each part to be a binary file transfer.

{%
include figure image_path="/assets/images/2024/02/multipart-request.svg"
caption="Multipart HTTP Request with 2 parts"
img_style="padding: 10px; background-color: white; height: 600px;"
%}

The intended use of a multipart upload is to handle multiple, small data fields, typically being user text input.
From a high level, the HTTP request is broken up into parts delimited by an arbitrary `boundary` sequence specified
within the `Content-Type` header.

There are multiple reasons this is unsuited to transferring large files:

* Content needs to be inspected for `boundary` occurrences,
* Unable to know name, count, or content-type of parts without parsing previous parts,
* No additional features supporting large files beyond those of the single request format.

The multipart form-data request should be considered a strictly worse version of putting the file content within the
request body. The cost to support multiple files within the request introduces the overhead of the data comparison
against the boundary. While [algorithms](https://en.wikipedia.org/wiki/String-searching_algorithm) exist to minimize 
overhead, it should generally be considered to be _O(n)_ where _n_ is the file content length. Expansions of HTTP with
HTTP/2 and HTTP/3 have removed request overhead so there is reason to use form-data to transfer anything but trivially
small files.

#### Real-World Examples: Amazon Alexa API

The [Amazon Alexa](https://developer.amazon.com/en-US/docs/alexa/alexa-voice-service/structure-http2-request.html) 
device makes use of multipart form-data requests to communicate with the Alexa Voice Service (AVS) servers. Every 
request includes JSON metadata and binary audio data as separate parts. There is an inherent incompatibility between
JSON and binary data: there are no efficient ways to embed binary data into the text format, and embedding JSON into
binary data requires an additional encoding mechanism. 

The binary encoding selected was multipart form-data, which would reduce complexity by being a well-supported part of 
HTTP and requiring no additional dependencies. While more efficient, binary encodings such as gRPC/protobuf, Thrift, 
and JSONB are often overlooked because of the need to add library dependencies for parsing, inspection, and debugging
of the over-the-wire data.

As mentioned earlier, the `boundary` mechanic creates a CPU bottleneck working against higher throughput. It should be 
noted that this inefficient processing can be omitted when the multipart format is of a particular type. When the only
binary part is at the end of the request, there would be no need to inspect the binary data for boundaries. With this
relaxation of the format, the multipart form-data encoding is just as performant as the binary encodings mentioned 
while retaining the human-readable over-the-write representation of the JSON part.

# Http4s EntityDecoder

The `org.http4s.EntityDecoder` trait includes the documentation:

> A type that can be used to decode a Message EntityDecoder is used to attempt to decode a Message returning the entire
> resulting A. If an error occurs it will result in a failed effect. The default decoders provided here are not
> streaming, but one could implement a streaming decoder by having the value of A be some kind of streaming construct.

It is also readily apparent from the `org.http4s.multipart.Multipart` implementation:

```scala
final case class Multipart[+F[_]](
       parts: Vector[Part[F]],
       boundary: Boundary,
     )
```

As mentioned earlier, multipart form-data requests are not a good mechanism to handle large file uploads. There has 
been no effort to optimize the streaming capabilities of the Http4s Multipart handler, opting to represent the parts
as an immutable `Vector`. To construct this instance, the entire request will need to be parsed, negating any request
streaming ability.

It is key to note that Out-Of-Memory issues are circumvented by way of temporary files, the implementation of this
decoder has chosen to create and manage filesystem objects as a way to offload large requests from system RAM. Http4s 
implicits can be explicitly created to modify default values for this mechanism, by creating a decoder using the 
`MultipartDecoder` static method `mixedMultipartResource`:

```scala
def mixedMultipartResource[F[_] : Concurrent : Files](
       headerLimit: Int = 1024,
       maxSizeBeforeWrite: Int = 52428800,
       maxParts: Int = 50,
       failOnLimit: Boolean = false,
       chunkSize: Int = 8192,
     ): Resource[F, EntityDecoder[F, Multipart[F]]]
``` 

# Streaming Multipart File Uploads with Http4s

There is no streaming issues when directly using the `request.body` exposed by Http4s. It is a `Stream[F, Byte]`, the 
issues mentioned in this post are concerning the included body decoders breaking streaming semantics. This shouldn't
be seen as criticism or oversight, as the direct parsing of the body is a cleaner, more direct and the recommended 
approach to handle streaming request data. Instead of the inefficient use of multipart form-data, the same effect is
better achieved by moving all text-based form-data to HTTP headers. But for the stubborn and niche use-cases such as
with Alexa above, can the Http4s multipart decoder be implemented to support streaming?

## Problems converting a Stream to Stream-of-Streams

An HTTP request body should be viewed as a single stream.  

//TODO: finish  

a single stream, how can a multipart body be represented using a stream? Conceptually it maps
to a `Stream[Part]` since parts will need to be accessed sequentially. But pragmatically, each part could represent
a large file which would also need to be a stream. A single `Stream` cannot map to a `Stream[Stream[_]]` since this
wouldn't allow each part, which can represent a large file, ecause we need to , but then what is a `Part`? It cannot be a s
to be as efficient as possible?  multipart request streaming be
properly implemented? With HTTP/2 multiplexing, is there even a plausible use-case to send multiple fields in a single
request?

//TODO: implementation details for streaming Multipart decoder  

## Testing Streams: Memory Use

Streams are programming abstractions which are difficult to test directly, as their output is identical to their 
collection counterparts. It is insufficient to test the final output is being streamed, since any intermediate 
transformation could have easily buffered results only to stream them again. The absolute test would be to measure 
memory use of the system, as any buffering will have a measurable effect on heap use. For the purpose of our testing, 
restricting the JVM heap size to be smaller than the stream data would indicate no intermediate buffering.
(This cannot detect buffering to the filesystem, which would require additional code inspection).

Running the JVM with a 128Mb memory allocation can use the `Xmx` parameter:

```shell
java -Xmx128m -jar http-maven-receiver-assembly-1.0.25.jar 
```

## Implementation

//TODO: Scala implementation  

# Conclusion

//TODO: conclusion