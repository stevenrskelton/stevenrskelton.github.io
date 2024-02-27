---
#layout: post
title: "Polymer Data-Binding Filters"
categories:
  - JavaScript
tags:
  - Web_Components
---

{% include postlogo.html title="Web Components" src="/assets/images/2014/02/webcomponents.png" %} One useful feature of
modern JavaScript libraries is 2-way data-binding. All interactive websites perform this functionality one way or
another, but only a few libraries such as [Ember.js](http://emberjs.com/), [AngularJS](http://angularjs.org/)
and [Polymer](http://www.polymer-project.org/) don’t require a single line of JavaScript.

2-way data-binding is the ability to keep two data sources in sync: if either changes the other will be automatically
updated with the same value. More complicated use cases involve conditional statements or formula to define the
relationship of the first and second values. Polymer Filters allow bidirectional binding even in these scenarios.

_The code in this post references Polymer 0.1.4, and may change in subsequent releases._
<script src="/assets/images/2014/02/platform-0.1.4.js"></script>
<script src="/assets/images/2014/02/polymer-0.1.4.js"></script>

One example would be to have 2 numbers which are a multiple of each other:

<polymer-element name="multiply-bind" attributes="by">
  <template>
    <input type="number" value="{{-"{{"-}}multiplyBind}}"> = 5 * <input type="number" value="{{-"{{"-}}multiplyBind | multiply(by)}}">
  </template>
  <script>
    PolymerExpressions.prototype.multiply = function(value, number) {
      return value / number;
    };
    PolymerExpressions.prototype.multiply.toModel = function(value, number) {
      return value * number;
    };
    Polymer('multiply-bind', { multiplyBind: 10 }); 
  </script>
</polymer-element>
<multiply-bind by="5"></multiply-bind>

But not everyone is writing a spreadsheet application. Filters can also be used to perform arbitrary transformations;
including text transformations.

<polymer-element name="simple-bind" noscript>
  <template>
    <strong>1:</strong> <input type="text" value="{{-"{{"-}}simpleBind}}"> <strong style="margin-left:2em">2:</strong><input type="text" value="{{-"{{"-}}simpleBind}}">
  </template>
</polymer-element>
<simple-bind></simple-bind>

```html
<input type="text" value="{{-"{{"-}}simpleBind}}">
<input type="text" value="{{-"{{"-}}simpleBind}}">
```

Polymer allows rudimentary logic to be performed within bind `{{-"{{"-}}expressions}}`; although only a subset of
JavaScript is allowed, and 2-way binding is disabled. If we wanted to use expressions to append hi! to the end of input
2’s value, it would disable its ability to be used to update input 1.

<polymer-element name="expression-bind" noscript>
  <template>
    <strong>1:</strong> <input type="text" value="{{-"{{"-}}expressionBind}}"> <strong style="margin-left:2em">2:</strong> <input type="text" value="{{-"{{"-}}!expressionBind ? '' : expressionBind+', hi!'}}">
  </template>
</polymer-element>
<expression-bind></expression-bind>

```html
<input type="text" value="{{-"{{"-}}expressionBind}}">
<input type="text" value="{{-"{{"-}}!expressionBind ? '' : expressionBind+', hi!'}}">
```

To retain 2-way binding we need a way to specify how to _undo_ our binding expression. This ability is handled by a
feature called [Filters](http://www.polymer-project.org/docs/polymer/filters.html). They allow both the forward _(
Model-to-Dom)_ and reverse _(Dom-to-Model)_ functions to be specified, using any valid JavaScript.

If 2-way binding isn’t required, the toDom definition can be omitted. Filters can be also used instead
of `{{-"{{"-}}expressions}}` when logic is too complex for the restricted set of operators `{{-"{{"-}}expressions}}` are
limited to.

<polymer-element name="filter-bind">
  <template>
    <strong>1:</strong> <input type="text" value="{{-"{{"-}}filterBind}}"> <strong style="margin-left:2em">2:</strong> <input type="text" value="{{-"{{"-}}filterBind | myCustomFilter}}">
  </template>
  <script>
    String.prototype.endsWith = function(suffix) {
        return this.indexOf(suffix, this.length - suffix.length) !== -1;
    };
    PolymerExpressions.prototype.myCustomFilter = function(value) {
      if(!value){
         return '';
      }else{
         return value + ', hi!';
      }
    };
    PolymerExpressions.prototype.myCustomFilter.toModel = function(value) {
      if(!value){
         return '';
      }else if(value.endsWith(', hi!')){
         return value.substring(0,value.length-5);
      }else{
         return value;
      }
    };
    Polymer('filter-bind');      
  </script>
</polymer-element>
<filter-bind></filter-bind>

```js
String.prototype.endsWith = function(suffix) {
  return this.indexOf(suffix, this.length - suffix.length) !== -1;
};
PolymerExpressions.prototype.myCustomFilter = function(value) {
  if(!value){
    return '';
  }else{
    return value + ', hi!';
  }
};
PolymerExpressions.prototype.myCustomFilter.toModel = function(value) {
  if(!value){
    return '';
  }else if(value.endsWith(', hi!')){
    return value.substring(0,value.length-5);
  }else{
    return value;
  }
};  
```

```html
<input type="text" value="{{-"{{"-}}filterBind}}">
<input type="text" value="{{-"{{"-}}filterBind | myCustomFilter}}">
```

Filters can also take additional arguments, any number of `{{-"{{"-}}expression}}` can be used. In the above example, we
don’t need to hard-code the `hi!`, it could come from another input.

<polymer-element name="args-bind">
  <template>
    <strong>Filter Argument</strong>: <input type="text" value="{{-"{{"-}}arg}}"/>
    <br style="margin-bottom:1em;"/>
    <strong>1:</strong> <input type="text" value="{{-"{{"-}}argsBind}}"> <strong style="margin-left:2em">2:</strong> <input type="text" value="{{-"{{"-}}argsBind | myCustomFilter1(arg)}}">
  </template>
  <script>
    String.prototype.endsWith = function(suffix) {
        return this.indexOf(suffix, this.length - suffix.length) !== -1;
    };
    PolymerExpressions.prototype.myCustomFilter1 = function(value, a) {
      if(!value){
         return '';
      }else{
         return value + ', ' + a;
      }
    };
    PolymerExpressions.prototype.myCustomFilter1.toModel = function(value, a) {
      if(!value){
         return '';
      }else if(value.endsWith(', ' + a)){
         return value.substring(0,value.length-2-a.length);
      }else{
         return value;
      }
    };
    Polymer('args-bind', { arg: 'hi!' });      
  </script>
</polymer-element>
<args-bind></args-bind>

```js
PolymerExpressions.prototype.myCustomFilter = function(value, a) {
  if(!value){
    return '';
  }else{
    return value + ', ' + a;
  }
};
PolymerExpressions.prototype.myCustomFilter.toModel = function(value, a) {
  if(!value){
    return '';
  }else if(value.endsWith(', ' + a)){
    return value.substring(0,value.length-2-a.length);
  }else{
    return value;
  }
};
```

```html
<input type="text" value="{{-"{{"-}}arg}}"/>
<input type="text" value="{{-"{{"-}}filterBind}}">
<input type="text" value="{{-"{{"-}}filterBind | myCustomFilter(arg)}}">
```

Filters are a very concise way to perform data transformations without manually writing code or adding additional fields
to the Web Component’s model. Polymer has a declarative style, and the use of filters for data manipulation further
expand the idea allowing clear declaration of intent.

