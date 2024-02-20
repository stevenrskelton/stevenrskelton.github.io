---
#layout: post
title: "Finagled Client and Multiplexed C# Thrift Server Bug"
categories:
  - Platform
  - Scala
tags:
  - Thrift
---

This is an obscure issue, with an easy workaround, but no obvious solution.

You chose the Finagle RPC stack because you have old and dusty C# applications mixed in with your Scala. It’s only
inevitable that one day you will connect a Finagle client to your C# server, and it won’t work.

If you:

- Are running
  a [TMultiplexedProtocol](https://github.com/apache/thrift/blob/master/lib/csharp/src/Protocol/TMultiplexedProtocol.cs)
  C# server, and
- Have hacked Finagle to support it,

Yes, your Scala-to-Scala, C#-to-C#, and C#-to-Scala work just fine.

Looking over
the [com.twitter.finagle.thrift.ThriftClientFramedCodec](https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/scala/com/twitter/finagle/thrift/ThriftClientFramedCodec.scala)
you spot the magic invocation
of [ThriftTracing.CanTraceMethodName](https://github.com/twitter/finagle/blob/master/finagle-thrift/src/main/scala/com/twitter/finagle/thrift/ThriftTracing.scala),
a fire-and-forget call to a mysterious `__can__finagle__trace__v3__` method.

The unsuspecting multiplexed non-finagle service throws an exception upon receiving a call which it cannot route, as it
does not conform to the `Service.Method` format.

Tracing is a great feature, and it makes sense Finagle does what it does (it uses this method upgrade the wire protocol
to support more advanced features). A simple workaround is to disable this feature, inherit the codecs: in
your `ClientBuilder()` constructor substitute these in for the call to `ThriftClientFramedCodec()`.

```scala
import com.twitter.finagle.{ CodecFactory, ClientCodecConfig, ServiceFactory }
import org.apache.thrift.protocol.{ TProtocolFactory, TBinaryProtocol }
class ThriftClientFramedCodecFactoryWithoutFinagle(
 _protocolFactory: TProtocolFactory = new TBinaryProtocol.Factory())
 extends CodecFactory[ThriftClientRequest, Array[Byte]]#Client {
 
 def apply(config: ClientCodecConfig) = new {} 
  with ThriftClientFramedCodec(_protocolFactory, config, None, false) {
 
  override def prepareConnFactory(
    underlying: ServiceFactory[ThriftClientRequest, Array[Byte]]) = underlying
 }
}
class ThriftClientBufferedCodecFactoryWithoutFinagle(
 _protocolFactory: TProtocolFactory = new TBinaryProtocol.Factory())
 extends CodecFactory[ThriftClientRequest, Array[Byte]]#Client {
 
 def apply(config: ClientCodecConfig) = new {} 
  with ThriftClientBufferedCodec(_protocolFactory, config) {
 
  override def prepareConnFactory(
    underlying: ServiceFactory[ThriftClientRequest, Array[Byte]]) = underlying
 }
}
```

In the builder, we change:

```
ClientBuilder().codec(ThriftClientFramedCodec()).Build()
```

to

```
ClientBuilder().codec(new ThriftClientFramedCodecFactoryWithoutFinagle()).Build()
```

After 10 months,
the [0.9.1 release of libthrift](https://search.maven.org/search?q=g:org.apache.thrift%20AND%20a:libthrift) is out, now
Finagle can officially support multiplexing!
