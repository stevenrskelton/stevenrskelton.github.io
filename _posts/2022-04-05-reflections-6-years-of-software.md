---
title: "Reflections: 6 Years of Software"
categories:
---
My last blog update was 6 years ago, and Javascript still seems popular. Is this a case of _There Is No Alternative_?

### Javascript and HTML

Javascript popularity is is driven by the web browsers, and an outlook on Javascript is based on a perception of HTML.
What is your view of HTML, is it:
1. [incomplete?](#html-is-incomplete)
2. [complete?](#html-is-complete)
3. [needs to be replaced!](#html-needs-to-be-replaced)

#### HTML is Incomplete
##### Custom Elements fixes that

HTML is limited as a markup language, static by nature, so scripting is a necessary. Javascript came in to make pages dynamic, as a glue between HTML DOM element `onevent` handlers. 

```html
<button onclick="myFunction()">Click me</button>
```

What about non-visual abstractions like server requests, undo stacks, state management, conditional DOM elements - these can't be HTML can they?  Scripting will be a necessity is some form, but that also doesn't mean abandon HTML and create 100% Javascript PWAs with no HTML.

What happened to the complexity of the `<button>` example above, is capturing a user interaction and firing an event not complex? What about a `<video>` tag, is downloading then decoding a binary stream, rendering using variable hardware acceleration not complex? Where is all of the Javascript?

Javascript doesn't _have_ to handle all of the complexity in a web page, what if the HTML elements themselves were more complex. 

Polymer and Web Components served their purpose.  Polymer started as a polyfil implementation of Web Components, built out a use case, and then slowly disappeared. The great ideas of Polymer and Web Compoents were moved into browsers as native support, like how `<button>` and `<video>` are nativily supported. The less useful features (HTML Imports, 2-way data binding) were removed from Polymer, and the remaining helper utilities were repackaged into the [Lit library](https://lit.dev/).

All browsers support the extension of HTML using `<custom-elements>`, and according to [https://custom-elements-everywhere.com/](https://custom-elements-everywhere.com/) the popular Javascript frameworks are almost there too.

There will always be a need for frameworks, internally a custom element can be massivily complex. And frameworks allow for divergent innovation necessary to test out new ideas. However standardization is also necessary to separate the good from the bad, and promote efficiency. It is in everyones best interest to have the freedom to mix React or Angular componts, and drop them into a Vue app without being an expert in all 3 of them.  Components shouldn't care how or where they are being used, and the fact that they do now is code smell for undeclared external dependencies.

### HTML is Complete
#### HTML has all the necessary primatives, most people just need templates

What if HTML had all of the primatives necessary?  Obviously people need more than a `<div>` and some CSS to make an app.  But what if `<custom-elements>` existed for everything you needed to do?

User-friendly editors like Wordpress, Shopify, or Wix have created succesful websites by abstracting complexity away - comment sections are drag and drop integrations to Disqus, Discourse and Facebook.  Simplicity can exist down to the code level, it shouldn't matter if programming is done in a UI or directly in the [Liquid templates](https://shopify.github.io/liquid/) the comment section shouldn't be something you need to worry about. 

We have all seen pictures of the 6 monitor "power user" setup, but for most people that is an uncomfortable amount of information.  Designing a webpage using 20 interdependent widgets can also be too much.  If all of that interdependent information is necessary, consider breaking up into different screens, or at a minimum isolate distinct focus sections.  In a lot of cases the complexity in the page that is forcing complexity into HTML code isn't even what users want.  Sometimes less is more. Robinhood users didn't want more blinking numbers crammed into a screen when making life changing financial decisions, they just wanted some great looking confetti.

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
