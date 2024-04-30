---
#layout: post
title: "DOM Manipulation using Web Components"
categories:
  - JavaScript
tags:
  - Web_Components
example: ordered-columns
---

{% include postlogo.html title="Web Components" src="/assets/images/2014/11/webcomponents.png" %} HTML elements are free
to change the style, size, and placement of their children, and even their order. A lot of advanced use cases define
rendering based on both the properties of element as well as the properties of their children; one particularly
interesting case is the 2 column timeline. This is similar to a standard 2 column flow, except instead of first filling
one column and overflowing to the second, columns are filled simultaneously – inserting elements into whichever has the
least content. The net effect is elements occurring earlier in the HTML markup are placed vertically higher in the page
than elements occurring later. The page reads top to bottom as a chronological timeline, which while being a simple
enough concept cannot be done using standard HTML. In fact, the exact ordering of elements are different based on widths
of the columns. Column placements are determined by previous element’s heights, and heights are a function of widths, so
setting column widths as a percent of the screen sizes means layouts must be recalculated for each resolution. A simple
way of doing this is using a Web Component.

The standard 2 column layout using [CSS3 columns](https://developer.mozilla.org/en-US/docs/Web/CSS/columns) has a
left-to-right rendering, which is suitable for continuous text (like a newspaper), but not for blocked content like
images or separate articles.

<style shim-shadowdom>
    article-timeline {
        margin-right: auto;
        margin-left: auto;
        width:721px;
        border:2px solid #333;
        display: block;
    }
    #csscolumns > article,
    article-timeline::shadow article {
        text-align: center;
        border: 3px solid #DC143C;
        border-radius:5px;
        margin: 2px 1px;
        font-size: 16px;
        font-weight: bold;
    }
    article-timeline::shadow .column {
        vertical-align: top;
        width: 50%;
        display: inline-block;
    }
    #csscolumns {
        margin-right: auto;
        margin-left: auto;
        width:721px;
        -moz-columns: 2;
        -webkit-columns: 2;
        columns: 2;
        -moz-column-gap: 0;
        -webkit-column-gap: 0;
        column-gap: 0;
        border:2px solid #333;
    }
</style>
<script src="/assets/images/2014/11/webcomponents-0.5.1.js"></script>
<script src="/assets/images/2014/11/polymer-0.5.1.js"></script>
<polymer-element name="article-timeline">
    <template>
        <div class="column"></div><div class="column"></div>
    </template>
    <script>
    "use strict";
    Polymer({
        ready: function(){
            var items = this.querySelectorAll('article');
            var columns = this.shadowRoot.querySelectorAll('.column');
            [].forEach.call(items, function(element){
                var column = columns[0];
                [].forEach.call(columns, function(c){
                    if(c.clientHeight < column.clientHeight) column = c;
                });
                column.appendChild(element);
            });
        }
    });
    </script>
</polymer-element>
<div id="csscolumns">
    <article style="line-height:60px">
        1
    </article>
    <article style="line-height:40px">
        2
    </article>
    <article style="line-height:75px">
        3
    </article>
    <article style="line-height:50px">
        4
    </article>
    <article style="line-height:62px">
        5
    </article>
    <article style="line-height:40px">
        6
    </article>
    <article style="line-height:43px">
        7
    </article>
</div>

Our goal is to create a top-down render.

<article-timeline>
    <article style="line-height:60px">
        1
    </article>
    <article style="line-height:40px">
        2
    </article>
    <article style="line-height:75px">
        3
    </article>
    <article style="line-height:50px">
        4
    </article>
    <article style="line-height:62px">
        5
    </article>
    <article style="line-height:40px">
        6
    </article>
    <article style="line-height:43px">
        7
    </article>
</article-timeline>

This requires a little knowledge of how HTML content behaviour is defined within a Polymer Web Component, along with
strategies of how we can define reordering of the output DOM elements.
Web components opt-in to child HTML content; if no behaviour is defined web components will ignore inner elements by
default. Polymer uses `<content>` elements to specify inclusion, where each `content` element can define a `select`
attribute containing a standard CSS selector. For example, a custom element that renders only child `<div>` elements
with a `show` CSS class could be defined as follows:

```html

<polymer-element name="div-with-show" noscript>
    <template>
        <content select="div.show"></content>
    </template>
</polymer-element>

<div-with-show>
    <div class="show">this div will render</div>
    <div>this div will be not be rendered</div>
    <span>other non-div elements will also not be rendered</span>
</div-with-show>
```

DOM Output:

```html

<div-with-show>
    <div class="show">this div will render</div>
</div-with-show>
```

Taking this one step further, we could render all elements matching a selector before others that don’t. All `<div>`
elements with a `first` class could be displayed before any other `<div>`s, irrespective of the order they were
initially in-lined in the DOM.

```html

<polymer-element name="first-then-second" noscript>
    <template>
        <content select="div.first"></content>
        <content select="div"></content>
    </template>
</polymer-element>

<first-then-second>
    <div>S1</div>
    <div class="first">F1</div>
    <div class="first">F2</div>
    <span>still will not be rendered</span>
    <div>S2</div>
</first-then-second>
```

DOM Output:

```html

<first-then-second>
    <div class="first">F1</div>
    <div class="first">F2</div>
    <div>S1</div>
    <div>S2</div>
</first-then-second>
```

More details on the usage of `<content>` elements is outlined
in [HTML5 Rocks’ Shadow DOM 101 article](http://www.html5rocks.com/en/tutorials/webcomponents/shadowdom/#toc-projection),
but the premise is simple, elements are rendered by the first matching `<content>` selector, if they don’t match any
they will not be rendered, and they will never be rendered more than one. This matched rendering is referred to as the
_distribution_.

So we have shown that child nodes can be reordered, but how advanced can we make it? Can we dynamically define an
ordering based on the current runtime state? Since `<content>` relies on fixed CSS selectors, we need to look to
JavaScript to gain flexibility.

Sidestepping `content`, we can directly reference the inlined DOM of a Polymer web component using JavaScript’s
new [this.querySelectorAll](https://developer.mozilla.org/en/docs/Web/API/Document.querySelectorAll), or simply
using `this.children`. Any elements defined within the web component are in
its [Shadow DOM](http://www.w3.org/TR/shadow-dom/), so are children of `this.shadowRoot`.

So let’s create a custom element with 2 columns defined within the shadow DOM:

```html

<polymer-element name="article-timeline">
    <template>
        <div class="column"></div>
        <div class="column"></div>
    </template>
    <script>
        "use strict";
        Polymer({
            ready: function(){
                var items = this.querySelectorAll('article');
                var columns = this.shadowRoot.querySelectorAll('.column');
                //TODO: move items into columns
            }
        });
    </script>
</polymer-element>
```

The algorithm to move `items` into `columns` is as simple as finding the column with the shortest height.

```js
[].forEach.call(items, function(element){
    var column = columns[0];
    [].forEach.call(columns, function(c){
        if(c.clientHeight < column.clientHeight) column = c;
    });
    column.appendChild(element);
});
```

With a little CSS to make things pretty, and are ready to use our new web component to dynamically layout our page.

```html

<article-timeline>
    <article style="line-height:60px">
        1
    </article>
    <article style="line-height:40px">
        2
    </article>
    <article style="line-height:75px">
        3
    </article>
    <article style="line-height:50px">
        4
    </article>
    <article style="line-height:62px">
        5
    </article>
    <article style="line-height:40px">
        6
    </article>
    <article style="line-height:43px">
        7
    </article>
</article-timeline>
```

{%
include downloadsources.html
src="/assets/images/2014/11/article-timeline.html"
%}
