---
title: "Reflections: 6 Years of Software"
categories:
---
Last blog update was 6 years ago. Javascript still seems popular...  

What is your view of HTML and all HTML elements, is it:
1. incomplete,
2. complete, or
3. needs to be replaced.

### HTML is Incomplete
#### Custom Elements fixes that

Polymer and Web Components served their purpose.  Polymer started as a polyfil implementation of Web Components, built out a use case, and then slowly disappeared.
The great ideas of Polymer and Web Compoents were moved into browsers as native support, as expansions of the standard browser-exposed Javascript and DOM.
The less useful features (HTML Imports, 2-way data binding) were removed, and the remaining helper utilities were repackaged into the [Lit library](https://lit.dev/).

The only unsettled Web Component now is Custom Elements, ie: &lt;custom-html-element&gt;

Support is looking pretty good across popular frameworks, according to [https://custom-elements-everywhere.com/](https://custom-elements-everywhere.com/).
Even React might be useable, even though their virtual DOM appoach would seem contradictory, but Facebook/Meta doesn't have the clout it used to.

### HTML is Complete
#### It has primatives, you build the rest

Maybe for some? For an amazing single-page app I would agree that a framework is necessary. But for a surprising number of use cases javascript might be avoidable.
Consider Shopify and their [Liquid template language](https://shopify.github.io/liquid/).  Add in a few custom elements, abstract a few things out to SaaS, and who even needs javascript?
I was happy to move my small blog to GitHub for hosting, dropping Wordpress for Liquid templates.  And if I wanted a comment section forget the DB and just use Disqus.

### HTML needs to be replaced
#### A new paradigm

Mobile apps are an alternative to Javascript and HTML.  Browser Extensions are being used for crypto (omg).  Typescript is getting more and more common. 
[WASM (WebAssembly)](https://en.wikipedia.org/wiki/WebAssembly) is supported in over 95% of browsers.  [ScalaJS](https://www.scala-js.org) is still progressing.
And [Flutter supports the web](https://flutter.dev/multi-platform/web).

According to the [2021 StackOverflow Survey](https://insights.stackoverflow.com/survey/2021):
> "50% indicating they have been coding for less than a decade, and more than 35% having less than five years in the trade"
> "60% of respondents learned how to code from online resources"

The market is saturated with Javascript developers, and managers gravitate to pools of talent.  Javascript isn't going anywhere, but there are viable alternatives now.
