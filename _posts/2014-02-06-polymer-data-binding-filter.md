---
#layout: post
title: "Polymer Data-Binding Filters"
categories:
  - Polymer
---

One useful feature of modern Javascript libraries is 2-way data-binding. All interactive websites perform this functionality one way or another, but only a few libraries such as [Ember.js](http://emberjs.com/), [AngularJS](http://angularjs.org/) and [Polymer](http://www.polymer-project.org/) don’t require a single line of Javascript.

2-way data-binding is the ability to keep two data sources in sync: if either changes the other will be automatically updated with the same value. More complicated use cases involve conditional statements or formula to define the relationship of the first and second values. Polymer Filters allow bi-directional binding even in these scenarios.

_The code in this post references Polymer 0.1.3, and may change in subsequent releases._

One example would be to have 2 numbers which are a multiple of each other:

10
 = 5 * 
2

But not everyone is writing a spreadsheet application. Filters can also be used to perform arbitrary transformations; including text transformations.

1: 
 2:

```html
<input type="text" value="{{-"{{"-}}simpleBind}}">
<input type="text" value="{{-"{{"-}}simpleBind}}">
```

Polymer allows rudimentary logic to be performed within bind `{{-"{{"-}}expressions}}`; although only a subset of Javascript is allowed, and 2-way binding is disabled. If we wanted to use expressions to append hi! to the end of input 2’s value, it would disable its ability to be used to update input 1.

1: 
 2: 

```html
<input type="text" value="{{-"{{"-}}expressionBind}}">
<input type="text" value="{{-"{{"-}}!expressionBind ? '' : expressionBind+', hi!'}}">
```

To retain 2-way binding we need a way to specify how to _undo_ our binding expression. This ability is handled by a feature called [Filters](http://www.polymer-project.org/docs/polymer/filters.html). They allow both the forward _(Model-to-Dom)_ and reverse _(Dom-to-Model)_ functions to be specified, using any valid Javascript.

If 2-way binding isn’t required, the toDom definition can be omitted. Filters can be also used instead of `{{-"{{"-}}expressions}}` when logic is too complex for the restricted set of operators `{{-"{{"-}}expressions}}` are limited to.

1: 
 2: 

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

Filters can also take additional arguments, any number of `{{-"{{"-}}expression}}` can be used. In the above example, we don’t need to hard-code the `hi!`, it could come from another input.

Filter Argument: 
hi!

1: 
 2: 

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

Filters are a very concise way to perform data transformations without manually writing code or adding additional fields to the Web Component’s model. Polymer has a declarative style, and the use of filters for data manipulation further expand the idea allowing clear declaration of intent.

