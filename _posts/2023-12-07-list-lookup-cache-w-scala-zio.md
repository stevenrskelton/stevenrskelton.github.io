---
title: "List Lookup Cache with Scala ZIO"
categories:
  - Scala
tags:
  - ZIO
  - AsyncExecution
excerpt_separator: <!--more-->
---

Cache introduction<!--more-->

{% comment %}
{% include table-of-contents.html height="100px" %}
{% endcomment %}

{%
include figure image_path="/assets/images/2023/12/list_cache.svg" class="figsvgpadding"
alt="Multiple Keys Cache"
caption="Multiple keys get request to cache, and retrieved by single lookup call"
img_style="padding: 8px;background: white;"
%}

# Introduction to the Existing ZIO Cache

The ZIO2 in-memory cache implementation is concise and straight-forward enough to fit into a [single file Cache.scala](https://github.com/zio/zio-cache/blob/series/2.x/zio-cache/shared/src/main/scala/zio/cache/Cache.scala).
A second class called [Lookup.scala](https://github.com/zio/zio-cache/blob/series/2.x/zio-cache/shared/src/main/scala/zio/cache/Lookup.scala) is the externalized implementation for `Key => ZIO[Environment, Error, Value]`

When the `get` method is called
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