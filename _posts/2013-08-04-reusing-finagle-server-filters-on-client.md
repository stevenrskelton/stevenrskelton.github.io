---
#layout: post
title: "Reusing Finagle Server Filters on the Client"
categories:
  - Platform
  - Scala
tags:
  - Thrift
---

When using Thrift, Finagle Filters on the client inherit from `SimpleFilter[ThriftClientRequest, Array[Byte]]`, while on the server they must inherit from `SimpleFilter[Array[Byte], Array[Byte]]`. In this article, we will demonstrate one approach to creating a dual-function filter without repeating code.

For most filters, reuse on both the client and server isn’t a problem. Adhering to a [single responsibility principle](http://http//en.wikipedia.org/wiki/Single_responsibility_principle) (the [S in SOLID](http://en.wikipedia.org/wiki/Solid_%28object-oriented_design%29)), client responsibilities shouldn’t overlap server responsibilities. But this is not true for orthogonal concerns; such as logging, encoding or security.

An example of a simple, but useful filter to use on both the client and server is one that will execute a defined action every request given the invoked method name. In a debugging/development environment, this could be used to print to console, allowing the developer to know when and what network traffic occurs. But it could also be used for audit logs, or to perform method specific actions on a request, such as compressing/encrypting the response – the list goes on.

On the server, the filter could be coded as:

```scala
class MethodNameFilter(action: String => Unit)
 extends SimpleFilter[Array[Byte], Array[Byte]] {
 
  def apply(request: Array[Byte], 
    service: Service[Array[Byte], Array[Byte]]): Future[Array[Byte]] = {
 
    val inputTransport = new TMemoryInputTransport(request)
    val iprot = new TBinaryProtocol(inputTransport)
    val msg = iprot.readMessageBegin
    action(msg.name)
    service(request)
  }
}
```

And on the client:

```scala
class MethodNameClientFilter(action: String => Unit)
 extends SimpleFilter[ThriftClientRequest, Array[Byte]] {
 
  def apply(request: ThriftClientRequest, 
    service: Service[ThriftClientRequest, Array[Byte]]): Future[Array[Byte]] = {
 
    val inputTransport = new TMemoryInputTransport(request.message)
    val iprot = new TBinaryProtocol(inputTransport)
    val msg = iprot.readMessageBegin
    action(msg.name)
    service(request)
  }
}
```

This filter isn’t a lot of code, but for a more complex filter duplicating the body of apply would not be best practice. Unfortunately, since `ThriftClientRequest` does not inherit from `Array[Byte]` we cannot use variance properties of the type parameters to cast a `Service[ThriftClientRequest, Array[Byte]]` into a `Service[Array[Byte], Array[Byte]]`. This is the major sticking point, as this is one of the parameters to the apply method. Without being able to write a wrapper class, we cannot take advantage of an implicit conversion, and since this cannot be solved using extensions, type class polymorphism also can’t bridge this divide. We need to rely on inheritance.

The first step to a good application of inheritance is to identify and abstract away all differences. We can leave `Req` as an unspecified generic type parameter of SimpleFilter, and allow our child inheriance classes to define it. Each child class will need specify how to convert their `Req` type into our preferred type `Array[Byte]`. This is no work for a server filter, and short work for a client since the binary of a `ThriftClientRequest` instance is stored in its message field.

Our abstract class will impliment our apply method, DRY.

```scala
class AbstractMethodNameFilter[Req](action: String => Unit, requestToByte: Req => Array[Byte])
  extends SimpleFilter[Req, Array[Byte]] {
 
  def apply(request: Req, 
    service: Service[Req, Array[Byte]]): Future[Array[Byte]] = {
 
    val binaryRequest = requestToByte(request)
    val inputTransport = new TMemoryInputTransport(binaryRequest)
    val iprot = new TBinaryProtocol(inputTransport)
    val msg = iprot.readMessageBegin
    action(msg.name)
    service(request)
  }
}
```

Now, our client and server classes reduce right down to single lines:

```scala
class MethodNameFilter(action: String => Unit)
  extends AbstractMethodNameFilter[Array[Byte]](action, x => x)
 
class MethodNameClientFilter(action: String => Unit)
  extends AbstractMethodNameFilter[ThriftClientRequest](action, x => x.message)
```

Having 3 classes instead of 1 is an obvious concession, but issues like this always come up because of the single inheritance limitation. This is why it’s smarter to favour traits, interfaces, and composition over inheritance.

{%
  include downloadsources.html
  src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Reusing-Finagle-Server-Filters-on-Client.scala"
%}
