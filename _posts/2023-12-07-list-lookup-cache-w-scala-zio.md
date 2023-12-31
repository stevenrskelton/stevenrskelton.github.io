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

Body 

{%
include figure image_path="/assets/images/2023/12/ziocache.svg" class="figsvgpadding"
alt="ZIO Cache"
caption="ZIO Cache implementation"
img_style="padding: 8px;background: white;"
%}

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