---
title: "Reflections: 6 Years of Software"
categories:
  - Javascript
---
My last blog update was 6 years ago, and Javascript still seems popular. Is this a case of _There Is No Alternative_?

## Javascript and HTML

Javascript popularity is is driven by the web browsers, and an outlook on Javascript is based on a perception of HTML.
What is your view of HTML, is it:
1. [Incomplete and requires Javascript?](#html-is-incomplete-and-requires-javascript)
2. [Complete and has the building blocks for web?](#html-is-complete-and-has-the-building-blocks-for-web)
3. [Needs to be replaced!](#html-needs-to-be-replaced)

## HTML is Incomplete and Requires Javascript
_**Custom Elements fixes that**_

HTML is limited as a markup language, static by nature, so scripting is a necessary. Javascript came in to make pages dynamic, as a glue between HTML DOM element `onevent` handlers.

```html
<button onclick="myFunction()">Click me</button>
```

People quickly realized that Javascript needed to exist is its own right, HTML couldn't represent non-visual abstractions like server requests, undo stacks, state management, or conditional DOM elements. But that also doesn't mean abandon HTML because it is too limiting to create 100% Javascript PWAs. The question to answer is how much Javascript is required to implement a complex web page?

To answer that question, it's important to recognize that HTML already handles more complexity than just UI markup. Consider the `<button>` example above, is capturing a user interaction and firing an event not complex? What about a `<video>` tag, is downloading then decoding a binary stream, rendering using system specific hardware acceleration not complex? Where is all of the Javascript?

Javascript doesn't _have_ to handle all of the complexity in a web page if there exist HTML elements that can be used instead. 

Polymer and Web Components served their purpose.  Polymer started as a polyfil implementation of Web Components, pioneered a use case, and then slowly disappeared. The great ideas of Polymer and Web Compoents were moved into browser native support, like how `<button>` and `<video>` are nativily supported. The less useful features such as HTML Imports and 2-way data binding were removed from Polymer.  What remained were snippets of helper utilities not applicable to all scenarios, so it made sense to repackage them as [Lit library](https://lit.dev/).

At this time, all browsers support the extension of HTML using `<custom-elements>`, and according to [https://custom-elements-everywhere.com/](https://custom-elements-everywhere.com/) the popular Javascript frameworks are almost there too.

There will always be a use for frameworks even in a world with custom elements. But where and when to apply them might change a little. Internally a custom element can be massivily complex application benefiting from the use of framework. Maybe a different framework maps better to the concerns of page scope than inside a custom elements. It is also in everyones best interest to have the freedom to mix components from different sources, it should be possible to drop in a React or Angular component into a Vue app without being an expert in how the component was implemented. 

The standardization to custom element can paradoxically allow diversity to flourish in Javascript libraries. Using it to break down monolythic PWAs from one large project to many smaller, independent, well-defined projects will promote the use of alternative Javascript libraries as developers are less locked in to the project's initial codebase. Native browser support means libraries can be more lightweight so more can be used without weighing down pagesize, and HTTP/2 multiplexing means separate projects can be maintained without blocking rendering. The Javascript in web pages might not be disappearing, however it might look a lot different in the future than it does now.

## HTML is Complete and has the Building Blocks for Web
_**HTML has all the necessary primatives, most people just need templates**_

What if HTML had all of the primatives necessary?  Obviously people need more than a `<div>` and some CSS to make an app.  But what if a `<custom-elements>` existed for everything you needed to code?

User-friendly editors like Wordpress, Shopify, or Wix have created succesful websites by abstracting complexity away - comment sections are drag and drop integrations to Disqus, Discourse and Facebook.  Simplicity should also be maintained down to code level, it shouldn't matter if programming is done in a UI or directly in the [Liquid templates](https://shopify.github.io/liquid/) there isn't a reason that same comment section needs to be something to worry about. 

We have all seen pictures of the 6 monitor "power user" setup, but for most people that is an uncomfortable amount of information.  Designing a webpage with 20 interdependent widgets can also be too much.  Consider multiple screens or isolated focus sections. The 20 interdependent widgets that were requiring a complex state management Javascript framework has now become 4 sets of 5 interdependent widgets. Would the same heavy-weight framework still be required? Robinhood users didn't want more blinking numbers crammed into a screen when making life changing financial decisions, they just wanted some great looking confetti.

It is natural to think addition is the only type of evolution, and website evolution is towards more intensive Javascript.  For some tasks this is true, but the majority of tasks today are to address underserved customers or digital transformation of existing businesses.  The quality and range of the tools available make it less of a race of technological innovation and more about delivering value.  Tasks are leveraging mature libraries and native browser standardizations, and offloading as much as possible to SaaS integrations.  Businesses can be successful with a nicely configured storefront built only using out-of-box customizations.  Websites can thrive being Javascript light and template heavy.

## HTML needs to be replaced
_**A new paradigm without Javascript or HTML**_

According to the [2021 StackOverflow Survey](https://insights.stackoverflow.com/survey/2021):
> "50% indicating they have been coding for less than a decade, and more than 35% having less than five years in the trade"

> "60% of respondents learned how to code from online resources"

The market is saturated with new Javascript developers every year and hiring managers gravitate to pools of talent.  Javascript remains the most popular programming language with strong momentum into the future, adding in Typescript and server-side NodeJS it looks unstoppable.

Outside the realm of transpiled Javascript languages such as Typescript or Coffeescript, there exist alternatives Javascript. Two languages that offer rigourous compiled programming languages and the ability to call existing Javascript libraries are [ScalaJS](https://www.scala-js.org) and [Elm](https://elm-lang.org/), however neither have found large scale support.  Performance applications, while a small niche,  are leaving Javascript behind to the fully native supported [WASM (WebAssembly)](https://en.wikipedia.org/wiki/WebAssembly), which while supporting practically every language through LLVM truely excels with non-garbage collected languages such as [Rust](https://www.rust-lang.org/) and C/C++. But for these approaches, their strengths also limit their general adoption.

The primary challenger to the HTML-Javascript ecosystem is the mobile app.  Newer easier to work with languages of Swift and Kotlin have drastically lowered the experience required of mobile app development.  Multi-platform support has made monumental improvements with [Flutter](https://flutter.dev/) which even offers [budding support for web](https://flutter.dev/multi-platform/web) without Javascript.  Flutter promises write once run anywhere with a highly capable typed programming language [Dart programming language](https://dart.dev/), but so does the Javascript-based React Native.

For every improvement in Javascript alternatives, Javascript is making equal steps forward. For developers who enjoy and are productive in Javascript the ecosystem continues its steady progression of improvement and innovation.  But for others, many who begrudgingly work in the Javascript language, others who may appeciate more robust languages to work in, there are finally competitive alternatives.

## Final Thoughts

I've done my share of full-stack development, worked on many different Javascript frameworks - popular ones, defunct ones and custom.  The vast majority of my work today isn't in Javascript, but as an observation: _if feels like time spent learning Javascript is lost time._

Consider vanilla Javascript and time lost to debugging dynamic types during refactors when a compiler could do it in a half a second. To avoid that, sink time into configuring transpiling from Typescript, but surprise you would be doing that anyways since you need ES6 but want to code in the latest spec since this isn't 2015.  Don't forget to bookmark _https://caniuse.com/_. All of these seem like good reasons to minimize the use of Javascript on the web and look for alternative approaches. I've tried to make the case that Web Components with `<custom-elements>` will reduce Javascript complexity not only by being a well supported standard, but by increasing the pool of compatible components that can be inserted into your projects.  The best Javascript code is code that has been debugged by someone else.





