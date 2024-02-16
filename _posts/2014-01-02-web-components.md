---
#layout: post
title: "Web Components"
categories:
  - JavaScript
tags:
  - WebComponents
---

{% include postlogo.html title="Web Components" src="/assets/images/2014/01/webcomponents.png" %} At the heart of a web
page, there are UI elements and these elements interact: with the user, each other, and the server.
Although [HTML5](http://dev.w3.org/html5/markup/elements.html) expanded the original set of elements to include audio,
video, and date pickers, there has been no standard way to define custom elements. Elements not specified in the HTML
specification have had no support thrusting this responsibility onto client-side and server-side web frameworks.

The [Web Components](http://w3c.github.io/webcomponents/explainer/) standard solves this issue by allowing the creation
of custom HTML elements, seamlessly integrating them into HTML markup as if they were part of the original
specification.

### Introductory Articles

- [A Guide to Web Components](http://css-tricks.com/modular-future-web-components/) _(doesn’t use polyfills so examples
  only work in Chrome)_

### Introductory Presentations

{% include video id="fqULJBBEVQE" provider="youtube" %}

{% include video id="0g0oOOT86NY" provider="youtube" %}

Even before browsers are updated, JavaScript libraries called [polyfills](http://en.wikipedia.org/wiki/Polyfill) are
allowing today’s browsers to support Web Components. In June 2013, Mozilla and Google polyfill libraries were
consolidated into a single library called [Polymer](http://www.polymer-project.org/) bringing Web Component support to
all modern browsers.

{% include video id="p1NpZ-0Op0w" provider="youtube" %}

With browsers natively supporting: custom HTML elements, data binding and templates; client-side web frameworks will
soon undergo a great simplification. In the meantime Web Components’ everything-is-in-the-DOM architecture
encapsulates all new features allowing it to coexist with methods employed by most JavaScript frameworks. Transitioning
between AngularJS directives or Bootstrap UI is seamless since Web Components cannot be differentiated from core HTML
tags, meaning today’s frameworks already offer full support – leaving no reason to delay migration.

Two web frameworks, Google Polymer and [Mozilla X-Tags](http://www.x-tags.org/) specifically target and are optimized
for Web Components, while other client-side JavaScript frameworks such as [AngularJS](http://angularjs.org/) are
committed to full support upon their next major release. However the main goal of Web Components is to simplify web
applications to an assembly of standalone components, a methodology for reuse and ease of configuration which will lower
the learning curve to new developers.

The open-source community for re-usable Web Components is hosted at [Custom Elements](http://customelements.io/), as
well there are bundled libraries such as [Mozilla Brick](http://mozilla.github.io/brick/).

