---
title: "List Lookup Cache with Scala ZIO"
categories:
  - Scala
tags:
  - ZIO
  - AsyncExecution
excerpt_separator: <!--more-->
---
Simple in-memory cache implementations map `Key => Value`.  Simple modifications can expand functionality to 
`Seq[Key] => Seq[Value]` efficiently and without introducing defects by choosing class composition instead of class 
inheritance.<!--more-->

{% include table-of-contents.html height="100px" %}

# Making `Seq[Key]` Calls Efficiently

There are many use-cases benefiting from mutiple key requests and lookups. A typical example would be listing a social 
media user's friend list, reading names and avatars from a profile cache. Different users can have different friends, 
but also friends in common. When requesting multiple profiles from cache, how can we only request profiles not in the 
cache from origin?  
{%
include figure image_path="/assets/images/2023/12/list_cache.svg" class="figsvgpadding"
alt="Multiple Keys Cache"
caption="Multiple keys get request to cache, and retrieved by single lookup call"
img_style="padding: 8px;background: white;"
%}
Conceptually this is simple, first read from cache, and any uncached in a single call to origin.

# Introduction to the Existing ZIO Cache

The ZIO in-memory cache implementation is concise enough to fit into a [single file Cache.scala](https://github.com/zio/zio-cache/blob/series/2.x/zio-cache/shared/src/main/scala/zio/cache/Cache.scala).
The external load mechanism is implemented in a second class [Lookup.scala](https://github.com/zio/zio-cache/blob/series/2.x/zio-cache/shared/src/main/scala/zio/cache/Lookup.scala) as a `Key => ZIO[Environment, Error, Value]` method.

When the `get` method is called, a `MapValue` is returned from the internal Map, otherwise a new entry is created containing a `Promise` to be loaded from the `Lookup`.
{%
include figure image_path="/assets/images/2023/12/ziocache.svg" class="figsvgpadding"
alt="ZIO Cache"
caption="ZIO Cache implementation"
img_style="padding: 8px;background: white;"
%}

# Composition over Inheritance

## Scala 3 `export` keyword

https://docs.scala-lang.org/scala3/reference/other-new-features/export.html

## 
{%
include figure image_path="/assets/images/2023/12/cache_composition.svg" class="figsvgpadding"
alt="List cache composition using ZIO Cache"
caption="ListLookupCache using composition pattern over a ZIO Cache implementation"
img_style="padding: 8px;background: white;"
%}

{%
include github_project.html
name="List Lookup ZIO Cache"
url="https://github.com/stevenrskelton/stevenrskelton.github.io/tree/main/assets/examples/2023/list-lookup-zio-cache"
description="See the complete Source Code on GitHub"
%}