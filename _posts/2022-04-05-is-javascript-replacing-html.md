---
title: "Is JavaScript Replacing HTML?"
categories:
  - JavaScript
---

Over time there has been an ebb and flow to the ratio of JavaScript:HTML used in websites. What motivates the change,
and where is this ratio ultimately headed?

JavaScript fumbled along during the last millenium with development boom times crippled by short-lived fads of
browser-wars incompatibility, Java Applets and Shockwave/Flash that made JavaScript use less popular. Up until 2008 the
ultimate barrier was unpredicable runtime execution that could make even properly engineered websites unresponse. That
year the V8 JavaScript Engine unlocked the performance necessary to go all in on JavaScript, and we finally got to
achieve the pinnacle of entry-level developement: the all JavaScript page.

```html
<html>
  <head><title>I ❤️ JS</title></head>
  <body><script src="/init.js"></script></body>
</html>
```

Is HTML a vestigial reminder of our past or are there reasons to keep it around?

## JavaScript and HTML

One would suppose that their view on JavaScript is influenced by their personal perception of HTML. Is yours that HTML
is:

1. [Incomplete: and requires JavaScript?](#html-is-incomplete-and-requires-javascript)
2. [Complete: and has the building blocks for web?](#html-is-complete-and-has-the-building-blocks-for-web)
3. [Needs to be replaced!](#html-needs-to-be-replaced) (I ❤️ JS)

## HTML is Incomplete and Requires JavaScript

_**Custom Elements fixes that**_

HTML is limited as a markup language, static by nature making scripting is a necessary. JavaScript came in to make pages
dynamic, as a glue between HTML DOM element `onevent` handlers.

```html
<button onclick="myFunction()">Click me</button>
```

People quickly realized that JavaScript needed to exist is its own right, HTML couldn't represent non-visual
abstractions like server requests, undo stacks, state management, or conditional DOM elements. There were imfamous early
failed attempts at HTML interactivity like the &lt;blink&gt; tag. However that doesn't necessarily mean we need to
abandon HTML completely to create 100% JavaScript PWAs. The question to answer is how much JavaScript is required to
implement a complex web page, and how much HTML is suitable as a document scaffolding?

To answer that question, it's important to recognize that HTML already handles more complexity than UI markup. Consider
the `<button>` example above, is capturing a user interaction and firing an event not complex? What about a `<video>`
tag, is downloading then decoding a binary stream, rendering using system specific hardware acceleration not complex?
Where is all of the JavaScript?

These are examples that prove JavaScript doesn't _have_ to handle all of the complexity in a web page, there already
exist HTML elements that do the heavy lifting.

Polymer and Web Components served their purpose. Polymer started as a polyfil implementation of Web Components,
pioneered a use case, and then slowly disappeared. The great ideas of Polymer and Web Compoents were moved into browser
native support, like how `<button>` and `<video>` are nativily supported. The less useful features such as HTML Imports
and 2-way data binding were removed from Polymer. What remained were snippets of helper utilities not applicable to all
scenarios, so it made sense to repackage them as [Lit library](https://lit.dev/).

At this time, all browsers support the extension of HTML using `<custom-elements>`, and according
to [https://custom-elements-everywhere.com/](https://custom-elements-everywhere.com/) the popular JavaScript frameworks
are almost there too.

There will always be a use for frameworks even in a world with custom elements. But where and when to apply them might
change a little. Internally a custom element can be massivily complex application benefiting from the use of framework.
Maybe a different framework maps better to the concerns of page scope than inside a custom elements. It is also in
everyones best interest to have the freedom to mix components from different sources, it should be possible to drop in a
React or Angular component into a Vue app without being an expert in how the component was implemented.

The standardization to custom element can paradoxically allow diversity to flourish in JavaScript libraries. Using it to
break down monolythic PWAs from one large project to many smaller, independent, well-defined projects will promote the
use of alternative JavaScript libraries as developers are less locked in to the project's initial codebase. Native
browser support means libraries can be more lightweight so more can be used without weighing down pagesize, and HTTP/2
multiplexing means separate projects can be maintained without blocking rendering. The JavaScript in web pages might not
be disappearing, however it might look a lot different in the future than it does now.

## HTML is Complete and has the Building Blocks for Web

_**HTML has all the necessary primatives, most people just need templates**_

What if HTML had all of the primatives necessary? Obviously people need more than a `<div>` and some CSS to make an app.
But what if a `<custom-elements>` existed for everything you needed to code?

User-friendly editors like Wordpress, Shopify, or Wix have created succesful websites by abstracting complexity away -
comment sections are drag and drop integrations to Disqus, Discourse and Facebook. Simplicity should also be maintained
down to code level, it shouldn't matter if programming is done in a UI or directly in
the [Liquid templates](https://shopify.github.io/liquid/) there isn't a reason that same comment section needs to be
something to worry about.

We have all seen pictures of the 6 monitor "power user" setup, but for most people that is an uncomfortable amount of
information. Designing a webpage with 20 interdependent widgets can also be too much. Consider multiple screens or
isolated focus sections. The 20 interdependent widgets that were requiring a complex state management JavaScript
framework has now become 4 sets of 5 interdependent widgets. Would the same heavy-weight framework still be required?
Robinhood users didn't want more blinking numbers crammed into a screen when making life changing financial decisions,
they just wanted some great looking confetti.

It is natural to think addition is the only type of evolution, and website evolution is towards more intensive
JavaScript. For some tasks this is true, but the majority of tasks today are to address underserved customers or digital
transformation of existing businesses. The quality and range of the tools available make it less of a race of
technological innovation and more about delivering value. Tasks are leveraging mature libraries and native browser
standardizations, and offloading as much as possible to SaaS integrations. Businesses can be successful with a nicely
configured storefront built only using out-of-box customizations. Websites can thrive being JavaScript light and
template heavy.

## HTML needs to be replaced

_**A new paradigm without JavaScript or HTML**_

According to the [2021 StackOverflow Survey](https://insights.stackoverflow.com/survey/2021):
> "50% indicating they have been coding for less than a decade, and more than 35% having less than five years in the
> trade"

> "60% of respondents learned how to code from online resources"

The market is saturated with new JavaScript developers every year and hiring managers gravitate to pools of talent.
JavaScript remains the most popular programming language with strong momentum into the future, adding in Typescript and
server-side NodeJS it looks unstoppable.

Outside the realm of transpiled JavaScript languages such as Typescript or Coffeescript, there exist alternatives
JavaScript. Two languages that offer rigourous compiled programming languages and the ability to call existing
JavaScript libraries are [ScalaJS](https://www.scala-js.org) and [Elm](https://elm-lang.org/), however neither have
found large scale support. Performance applications, while a small niche, are leaving JavaScript behind to the fully
native supported [WASM (WebAssembly)](https://en.wikipedia.org/wiki/WebAssembly), which while supporting practically
every language through LLVM truely excels with non-garbage collected languages such
as [Rust](https://www.rust-lang.org/) and C/C++. But for these approaches, their strengths also limit their general
adoption.

The primary challenger to the HTML-JavaScript ecosystem is the mobile app. Newer easier to work with languages of Swift
and Kotlin have drastically lowered the experience required of mobile app development. Multi-platform support has made
monumental improvements with [Flutter](https://flutter.dev/) which even
offers [budding support for web](https://flutter.dev/multi-platform/web) without JavaScript. Flutter promises write once
run anywhere with a highly capable typed programming language [Dart programming language](https://dart.dev/), but so
does the JavaScript-based React Native.

For every improvement in JavaScript alternatives, JavaScript is making equal steps forward. For developers who enjoy and
are productive in JavaScript the ecosystem continues its steady progression of improvement and innovation. But for
others, many who begrudgingly work in the JavaScript language, others who may appeciate more robust languages to work
in, there are finally competitive alternatives.

## Final Thoughts

I've done my share of full-stack development, worked on many different JavaScript frameworks - popular ones, defunct
ones and custom. The vast majority of my work today isn't in JavaScript, but as an observation: _if feels like time
spent learning JavaScript is lost time._

Consider vanilla JavaScript and time lost to debugging dynamic types during refactors when a compiler could do it in a
half a second. To avoid that, sink time into configuring transpiling from Typescript, but surprise you would be doing
that anyways since you need ES6 but want to code in the latest spec since this isn't 2015. Don't forget to bookmark
_https://caniuse.com/_. All of these seem like good reasons to minimize the use of JavaScript on the web and look for
alternative approaches. I've tried to make the case that Web Components with `<custom-elements>` will reduce JavaScript
complexity not only by being a well supported standard, but by increasing the pool of compatible components that can be
inserted into your projects. The best JavaScript code is code that has been debugged by someone else.





