---
#layout: post
title: "Separation of Concerns with Finagle"
categories:
  - Thrift
  - Scala
---

The [Separation of Concerns (SoC)](http://en.wikipedia.org/wiki/Separation_of_concerns) pattern is one of those software architectural choices that everyone is helpful. It increases clarity, shortens the amount of code in the working context, and minimizes the chance of side effects. For example, two concerns that shouldn’t require entanglement: updating data and cache invalidation. Both are related, but one is concerned about business logic and database access, while the other deals with the cache servers. Finagle’s generated `FutureIface` can be used to keep these two separate.

Some techniques, such as [Type Class](http://en.wikipedia.org/wiki/Type_class) polymorphism (ie: .Net’s Extension Methods), or [Aspect-Oriented Programming (AOP)](http://en.wikipedia.org/wiki/Aspect-oriented_programming) necessitate a new way of thinking – and a lot of practice to be correctly applied. On the other hand, using files to separate code, such as with a Scala `trait` (or C# Partial Classes) is an approach intuitive to most developers. Our approach is similiar: create a separate class for each concern, and let Finagle wire them together for us.

We know that Scrooge generates an interface for every service, let’s extend the interface for an arbitrary service `Foo` into two classes.

```scala
class FooService extends FooApi.FutureIface {
  def getFoo(id: Int): Future[Foo] = {  ... }
  def updateFoo(foo: Foo): Future[Boolean] = {  ... }
}
class PostFooService extends TestApi.FutureIface {
  def getFoo(id: Int): Future[Foo] = Future.never
  def updateFoo(foo: Foo): Future[Boolean] = Future.never
}
```

In the foo service above, we have one class `FooService` containing all our data logic (ie: `{ ... }`), and a second class `PostFooService` handling an orthogonal concern. The `PostFooService` will contain any code that should be executed after (ie: post) a method call in `FooService`. Because we are reusing the `FutureIface` both classes will have access to the exact same input parameters. For simplicity, let’s assume that our post action is cache invalidation.

Right now all “post” methods return `Future.never`, which is ok since we will never try resolve it. The only point of the post class is to execute code, the original service implementation handles any return values. The `Future.never` satisfies Scala’s type checking, returning a `Future[Nothing]`, this matches any thrift return type. We could have also used null, but given null‘s stigma in Scala I think this is a better choice.

The only implementation necessary in the post class is for `updateFoo`, since we don’t expect a `get` method to trigger cache invalidation. Without loss of generality, we’ll call a method in an arbitrary external object called `OurCacheManager` to handle the underlying details – we are only concerned about demonstrating use patterns at the moment.

```scala
def updateFoo(foo: Foo): Future[Boolean] = {
  OurCacheManagerObject.invalidateFooById(foo.id)
  Future.never
}
```

All of the work coordinating calls and executing methods will be handled by Finagle. A [Finagle Filter](https://github.com/twitter/finagle/blob/master/finagle-core/src/main/scala/com/twitter/finagle/Filter.scala) is perfect for chaining together service calls, the code could not be any more succinct.

```scala
class PostConcernFilter(postConcernService: Service[Array[Byte], Array[Byte]]) 
extends SimpleFilter[Array[Byte], Array[Byte]] {
 
  def apply(request: Array[Byte], service: Service[Array[Byte], Array[Byte]]): Future[Array[Byte]] = {
    service(request).onSuccess(_ => postConcernService(request))
  }
}
```

The key idea to recognize is that we are instantiating a second Service – don’t worry, a Scrooge generated Service has nothing to do with the network or external resources, so the overhead is minuscule. The final filter code should be easy to follow, we have two services, the original business logic service that we always have, and a second service that we will relay the original request input to if and only if the first service call was successful. A Future’s `onSuccess` method allows us to register this callback, and since callbacks never directly return a value we are safe returning whatever we want (ie: a `Future.never`).

The builders for our service are:

```scala
val filter = new PostConcernFilter(
  new FooApi$FinagleService(new PostFooService, new TBinaryProtocol.Factory))
 
val server = ServerBuilder()
  .codec(ThriftServerFramedCodec())
  .name("Foo")
  .bindTo(socket)
  .build(filter andThen new FooApi$FinagleService(new FooService, new TBinaryProtocol.Factory))
```

It’s important to note that this can be used for any functionality, whether it is to run post method call, pre method call, or both. The most common use case for a post action is cache invalidation, but other uses include audit trails, notifications, or triggering external resources to synchronize.

We attached a callback to the `onSuccess` event, but there are also `onFailure` and `ensure` events that are useful. Some uses of these two might be notifications, resource cleanup, or integrity checks.

{%
  include downloadsources.html
  src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Separation-of-Concerns-with-Finagle.scala"
%}
