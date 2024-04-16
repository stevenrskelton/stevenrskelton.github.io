---
title: "Realtime Client Database using gRPC Bi-Directional Streams and ZIO Hub"
categories:
  - Scala
tags:
  - ZIO
  - Non-Blocking/Concurrency
excerpt_separator: <!--more-->
examples:
  - realtime-database-zio-hub-grpc
---

Realtime pushed-based databases such as [Google Firebase](https://firebase.google.com/docs/database) are a convenient
way to ensure clients have the most recent data locally. Data updates are automatically streamed to clients
immediately as they happen, or in the case of a client disconnect, immediately after reconnecting.

[gRPC server streaming](https://grpc.io/docs/what-is-grpc/core-concepts/#server-streaming-rpc)
and [ZIO Hub](https://zio.dev/reference/concurrency/hub/) allow this functionality to be easily replicated and
customized beyond what expensive paid-for services such as Firebase can do.<!--more-->

{% include table-of-contents.html height="100px" %}

# Client demands for data streaming

The typical simple web-based client-server communication pattern is for data to be requested by the client. When
clients want new data or otherwise interact, it will initiate a new request to the server. But as server technology 
and hardware capacities have increased, user expectations have increased to expect all client UIs to present realtime
data without the friction of manually requesting data updates. The typical client-server communication is slowly 
evolving into the stream of data in both directions between the client and server.

# gRPC Server Bi-Directional Streaming using HTTP/2

The evolution of technology has resulted in 2 technology standards for web-based bi-directional communications:  
[WebSockets](https://en.wikipedia.org/wiki/WebSocket) and HTTP/2 streams.

WebSockets were created first as the ability for a standard HTTP/1.1 connection to upgrade to support bi-directional 
client-server streaming. This is still the best approach for browser-server communications because of its clear 
JavaScript APIs within all browsers, backwards compatibility for HTTP/1.1-only clients, and its ability to take 
advantage of performance improvements offered by HTTP/2 and beyond.

For non-browser communications, such as with mobile apps or inter-server communication WebSockets is an unnecessary 
layer. As WebSockets runs over HTTP, because HTTP/2 has directly integrated multiplexed streaming capabilities it is 
better for abstraction libraries such as gRPC to directly support HTTP/2 instead of the higher-level WebSocket layer.

## ZIO Hub for Concurrency and Subscriptions

//TODO:

{%
include figure image_path="/assets/images/2024/04/realtime_database.svg"
caption="Realtime database pushing updates to clients using bi-directional gRPC Streams"
img_style="padding: 10px; background-color: white; height: 320px;"
%}