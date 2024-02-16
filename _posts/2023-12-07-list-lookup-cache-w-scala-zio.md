---
title: "List Lookup Cache with Scala ZIO"
categories:
  - Scala
tags:
  - ZIO
  - AsyncExecution
excerpt_separator: <!--more-->
examples:
  - list-lookup-zio-cache
---
In-memory caches mapping `Key => Value` are a simple and versitile tool to reduce number of calls to an origin 
datasource.  There are many use-cases requiring multiple cache calls, prefering `Seq[Key] => Seq[Value]`. Can a standard
cache implementation be expanded to efficiently handle this scenario?<!--more-->

{% include table-of-contents.html height="100px" %}

# Making `Seq[Key]` Calls Efficiently

## Example Use-Case

A use-case prefering `Seq[Key] => Seq[Value]` is a social media user's friend list, reading profiles from cache.
Different users will have different friends but popular users will appear in many friend lists. These popular users 
should be loaded from cache and not the origin, however this complicates our cache implementation. Each `Seq[Key]` 
request needs to be broken down into individual keys, sending each `Key` to cache when it exists, and then fetching
any remaining uncached keys from origin in a single `Seq[Key]` call.  The response will be the aggregated responses from
the multiple calls to cache and the call to origin. 

{%
include figure image_path="/assets/images/2023/12/list_cache.svg" class="figsvgpadding"
alt="Multiple Keys Cache"
caption="Multiple keys get request to cache, and retrieved by single lookup call"
img_style="padding: 8px;background: white;"
%}

# The Existing ZIO Cache

## ZIO Cache Interfaces

The ZIO in-memory cache implementation concisely fits into a single file [Cache.scala](https://github.com/zio/zio-cache/blob/series/2.x/zio-cache/shared/src/main/scala/zio/cache/Cache.scala).

```scala
/**
 * A `Cache` is defined in terms of a lookup function that, given a key of
 * type `Key`, can either fail with an error of type `Error` or succeed with a
 * value of type `Value`. Getting a value from the cache will either return
 * the previous result of the lookup function if it is available or else
 * compute a new result with the lookup function, put it in the cache, and
 * return it.
 *
 * A cache also has a specified capacity and time to live. When the cache is
 * at capacity the least recently accessed values in the cache will be
 * removed to make room for new values. Getting a value with a life older than
 * the specified time to live will result in a new value being computed with
 * the lookup function and returned when available.
 *
 * The cache is safe for concurrent access. If multiple fibers attempt to get
 * the same key the lookup function will only be computed once and the result
 * will be returned to all fibers.
 */
abstract class Cache[-Key, +Error, +Value] {

  /**
   * Returns statistics for this cache.
   */
  def cacheStats(implicit trace: Trace): UIO[CacheStats]

  /**
   * Returns whether a value associated with the specified key exists in the
   * cache.
   */
  def contains(key: Key)(implicit trace: Trace): UIO[Boolean]

  /**
   * Returns statistics for the specified entry.
   */
  def entryStats(key: Key)(implicit trace: Trace): UIO[Option[EntryStats]]

  /**
   * Retrieves the value associated with the specified key if it exists.
   * Otherwise computes the value with the lookup function, puts it in the
   * cache, and returns it.
   */
  def get(key: Key)(implicit trace: Trace): IO[Error, Value]

  /**
   * Computes the value associated with the specified key, with the lookup
   * function, and puts it in the cache. The difference between this and
   * `get` method is that `refresh` triggers (re)computation of the value
   * without invalidating it in the cache, so any request to the associated
   * key can still be served while the value is being re-computed/retrieved
   * by the lookup function. Additionally, `refresh` always triggers the
   * lookup function, disregarding the last `Error`.
   */
  def refresh(key: Key): IO[Error, Unit]

  /**
   * Invalidates the value associated with the specified key.
   */
  def invalidate(key: Key)(implicit trace: Trace): UIO[Unit]

  /**
   * Invalidates all values in the cache.
   */
  def invalidateAll: UIO[Unit]

  /**
   * Returns the approximate number of values in the cache.
   */
  def size(implicit trace: Trace): UIO[Int]
}
```

The origin datasource is implemented in another [Lookup.scala](https://github.com/zio/zio-cache/blob/series/2.x/zio-cache/shared/src/main/scala/zio/cache/Lookup.scala) class as a `Key => ZIO[Environment, Error, Value]` method.

```scala
/**
 * A `Lookup` represents a lookup function that, given a key of type `Key`, can
 * return a `ZIO` effect that will either produce a value of type `Value` or
 * fail with an error of type `Error` using an environment of type
 * `Environment`.
 *
 * You can think of a `Lookup` as an effectual function that computes a value
 * given a key. Given any effectual function you can convert it to a lookup
 * function for a cache by using the `Lookup` constructor.
 */
final case class Lookup[-Key, -Environment, +Error, +Value](lookup: Key => ZIO[Environment, Error, Value])
    extends (Key => ZIO[Environment, Error, Value]) {

  /**
   * Computes a value for the specified key or fails with an error.
   */
  def apply(key: Key): ZIO[Environment, Error, Value] = lookup(key)
}
```

## ZIO Cache Implementation

When the `get` method is called, if a value exists in the internal `Map` then it will be returned. Otherwise, a new 
`MapValue` is created and stored into the internal `Map`.  The `MapValue` contains a `Promise` of the origin data, 
which will be returned from a `Lookup` query.

{%
include figure image_path="/assets/images/2023/12/ziocache.svg" class="figsvgpadding"
alt="ZIO Cache"
caption="ZIO Cache implementation"
img_style="padding: 8px;background: white;"
%}

# Composition over Inheritance

## Motivation

## Scala 3 `export` keyword

https://docs.scala-lang.org/scala3/reference/other-new-features/export.html

https://en.wikipedia.org/wiki/Composite_pattern


# `ListLookupCache` Implementation

The standard cache has an origin lookup of the form `Key => Value`. When an origin accepts multiple keys, we can
adapt this to a `Seq[Key] => Map[Key, Value]` lookup recognizing backwards compatibility exists to the standard cache 
interface:

```scala
def lookupAll(keys: Seq[Key]): Seq[(Key, Value)]

def lookup(key: Key): Value = lookupAll(Seq(key)).head._2
```

## New Methods and Classes

A list cache implementation could expand the ZIO `Cache` by abstract interface inheritance and adding a single 
method:

```scala
abstract class ListLookupCache[Key, +Error, +Value] extends Cache[Key, Error, Value] {
  def getAll(k: Seq[Key])(implicit trace: Trace): IO[Error, Map[Key, Value]]
}
```

The static factory method to construct the cache should be adapted to take a multi-value `Lookup` class:

```scala
final case class ListLookup[Key, -Environment, +Error, +Value](lookupAll: Seq[Key] => ZIO[Environment, Error, Seq[(Key, Value)]])
```

{%
include figure image_path="/assets/images/2023/12/cache_composition.svg" class="figsvgpadding"
alt="List cache composition using ZIO Cache"
caption="ListLookupCache using composition pattern over a ZIO Cache implementation"
img_style="padding: 8px;background: white;"
%}

## Fallback to ZIO Cache

## Testing

Since our `ListLookupCache` was implemented using composition, leaving the ZIO cache implementation unchanged, there is 
no need to test any of the unchanged methods.  Tests are only required for the new method, `getAll`.

{%
include figure image_path="/assets/images/2023/12/list_cache_tests.svg" class="figsvgpadding"
alt="List cache tests"
caption="List cache tests to load key lookup and interaction of `getAll` and `get`"
img_style="padding: 8px;background: white;"
%}

### Using ScalaTest

### Using ZIO Test


<hr>

All ZIO code is included under permission of the Apache License, Version 2.0
```
/*
 * Copyright 2020-2023 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```


