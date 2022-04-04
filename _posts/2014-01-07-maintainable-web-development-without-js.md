---
#layout: post
title: "Maintainable Web Development without Javascript"
categories:
  - Dart
---

In the years 2002-2003, Internet Explorer captured 95% of world-wide browser market share. It was unfathomable to many that over the next 10 years IE would decline to just over 15%.
![World-Wide 2008 to 2014](/assets/images/2014/01/StatCounter-browser-ww-monthly-200812-201312.jpg)

The open-source community perceived a problem with yesterday’s browsers and put forth a solution – alternative browsers took root despite the best efforts of the domineering incumbent. In this light it would seem silly to think there isn’t yet another radical change waiting in the wings of today’s browser landscape. As all major browsers automatically upgrade to maintain an up-to-date “evergreen” status, the barrier to change has never been lower.

### Evolution Away From JavaScript
Javascript is the only game in town. Even with all its design inadequacies and outright logical flaws, client-side programming can only be done in Javascript. An army of libraries attempt to [monkey-patch](http://en.wikipedia.org/wiki/Monkey-patch) fixes, such as [jQuery](http://jquery.com/), [Underscore](http://underscorejs.org/), [FuncJS](http://funcjs.webege.com/), [Require](http://requirejs.org/). The diverse array of choices only muddy the picture as each address an overlapping portion of responsibility – users are often left sorting out conflicting combinations in an attempt to recreate a full-feature language.

Other attempts to creating a workable veneer are [transpiled](http://en.wikipedia.org/wiki/Transpile) languages, tools generating Javascript from a purportedly cleaner source. They range from simple language augmentations fixing the obvious: [Coffeescript](http://coffeescript.org/) and Microsoft [Typescript](http://www.typescriptlang.org/), to full language replacements: Google [Dart](https://www.dartlang.org/) and [emscripten](https://github.com/kripken/emscripten/wiki).

### Google Dart

The best chance of mass adoption comes from Dart. It is a mature, high-performance language pragmatically designed from the ground up to address Google’s own web development. The Chrome browser represents ⅓ of users so client-support is assured.

<iframe src="//www.youtube.com/embed/FqsU3TbUw_s" webkitallowfullscreen="" mozallowfullscreen="" allowfullscreen="" class="aligncenter" width="720" height="405" frameborder="0"></iframe>

![Web Components](/assets/images/2014/01/AngularDart.png) AngularDart with all transpiled languages, browser adoption is an immaterial gauge of success, the fair measure lies in developer adoption. On this front, the popular AngularJS web framework has quietly been supplanted by its Dart port, [Angular.Dart](https://github.com/angular/angular.dart). As developers are promised newer features and increased productivity only available in the Dart stream the impedance to switch is reversed. Attesting to Dart’s prime-time status, Google’s Lars Bak addressed the 2013 Devoxx conference with the [Shaping the Future of Web Development – Dart 1.0](http://parleys.com/p/52a9897ce4b04354fb7e57d0) keynote.

Also making the leap to Dart as its premier language is [Google Polymer](http://www.polymer-project.org/) library, the supporting library for the rapidly developing, landscape changing, [Web Components](http://stevenskelton.ca/web-components/) standard.

<iframe src="//player.vimeo.com/video/74391396" webkitallowfullscreen="" mozallowfullscreen="" allowfullscreen="" class="aligncenter" width="720" height="405" frameborder="0"></iframe>

### Scala-to-Javascript

While efforts into the [emscripten](https://github.com/kripken/emscripten/wiki) Javascript transpiler allow all [LLVM](http://en.wikipedia.org/wiki/LLVM) languages (which includes Java and Scala) to compile to Javascript, there are two projects specifically designed to bring Scala to the browser. While both aren’t yet production ready like Dart, they offer a promising glimpse to a more diverse future.

- [Scala.js](http://www.scala-js.org/) compiles Scala code to JavaScript, allowing you to write your Web application entirely in Scala.
- [JScala](https://github.com/nau/jscala) Scala macro that produces JavaScript from Scala code.

Although no alternative has reached critical mass, the game is still early, and the future platform for web development is ripe for change. HTML5 and [WebGL](http://davidwalsh.name/webgl-demo) have raised the complexity of the modern web page to the point where the development environment is now a limiting factor. The single language option is having a meaningful effect on future web development. Javascript may represent 95% of the web today, but it would be naïve to expect that not to change.

