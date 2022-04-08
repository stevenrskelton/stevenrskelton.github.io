---
title: "Reflections: 6 Years of Software"
categories:
---
My last blog update was 6 years ago, and Javascript still seems popular. Is this a case of _There Is No Alternative_?

### Javascript and HTML

Javascript popularity is is driven by the web browsers, and an outlook on Javascript is based on a perception of HTML.
What is your view of HTML, is it:
1. [Incomplete and requires Javascript?](#html-is-incomplete-and-requires-javascript)
2. [Complete and has the building blocks for web?](#html-is-complete-and-has-the-building-blocks-for-web)
3. [Needs to be replaced!](#html-needs-to-be-replaced)

#### HTML is Incomplete and Requires Javascript
##### Custom Elements fixes that

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

The standardization to custom element can paradoxically allow diversity to flourish in Javascript libraries. Using it to break down monolythic PWAs from one large project to many smaller, independent, well-defined projects will promote the use of alternative Javascript libraries as developers are less locked in to the project's initial codebase. Native browser support means libraries can be more lightweight so more can be used without weighing down pagesize, and HTTP/2 pipelining means separate projects can be maintained without blocking rendering. The Javascript in web pages might not be disappearing, however it might look a lot different in the future than it does now.


### HTML is Complete and has the Building Blocks for Web
#### HTML has all the necessary primatives, most people just need templates

What if HTML had all of the primatives necessary?  Obviously people need more than a `<div>` and some CSS to make an app.  But what if a `<custom-elements>` existed for everything you needed to code?

User-friendly editors like Wordpress, Shopify, or Wix have created succesful websites by abstracting complexity away - comment sections are drag and drop integrations to Disqus, Discourse and Facebook.  Simplicity should also be maintained down to code level, it shouldn't matter if programming is done in a UI or directly in the [Liquid templates](https://shopify.github.io/liquid/) there isn't a reason that same comment section needs to be something to worry about. 

We have all seen pictures of the 6 monitor "power user" setup, but for most people that is an uncomfortable amount of information.  Designing a webpage with 20 interdependent widgets can also be too much.  Consider multiple screens or isolated focus sections. The 20 interdependent widgets that were requiring a complex state management Javascript framework has now become 4 sets of 5 interdependent widgets. Would the same heavy-weight framework still be required? Robinhood users didn't want more blinking numbers crammed into a screen when making life changing financial decisions, they just wanted some great looking confetti.


It is natural to think evolution means addition, but that is an incorrect assumption, and more Javascript doesn't more Javascript doesn't the constant emergance of new lightweight Javascript frameworks 

If components are well designed the Javascript at the page level can be minimal. Not every site can be coded in a UI or using only templates, most will still benefit from the structure offered by a Javascript framework.  As functionality moves natively browser, frameworks are more lightweight, and there is less emphasis on Javascript.

### HTML needs to be replaced
#### A new paradigm without Javascript or HTML

Mobile apps are an alternative to Javascript and HTML, so the idea of a world without either isn't crazy.  It probably isn't what we want either, they did get us this far. 

But it might be time to tap the breaks a little, with enthusists going so far as to create Browser Extensions to manage your crypto holdings.

Typescript is getting more and more common. 
[WASM (WebAssembly)](https://en.wikipedia.org/wiki/WebAssembly) is supported in over 95% of browsers.  [ScalaJS](https://www.scala-js.org) is still progressing.
And [Flutter supports the web](https://flutter.dev/multi-platform/web).

According to the [2021 StackOverflow Survey](https://insights.stackoverflow.com/survey/2021):
> "50% indicating they have been coding for less than a decade, and more than 35% having less than five years in the trade"
> "60% of respondents learned how to code from online resources"

The market is saturated with Javascript developers, and managers gravitate to pools of talent.  Javascript isn't going anywhere, but there are viable alternatives now.
