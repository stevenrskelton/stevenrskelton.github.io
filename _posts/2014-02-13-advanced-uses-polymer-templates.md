---
layout: post
title: "Advanced Uses of Polymer Templates"
download_sources:
  - /assets/images/2014/02/templated-table-polymer-web-components.html
  - /assets/images/2014/02/templated-sortable-table.html
categories:
  - Polymer
---

Most sortable HTML table generators (such as AngularJS’s [ng-grid](http://angular-ui.github.io/ng-grid/)) allow cells to be customized and formatted according to templates, however all templates are specified as parsed strings of HTML content. The Web Components specification allows for [HTML Templates](http://www.w3.org/TR/components-intro/#template-section), meaning actual HTML fragments can be used instead of pieced together strings. Two benefits are better readability, CSS encapsulation by way of [Shadow DOM](http://www.w3.org/TR/components-intro/#shadow-dom-section), and soon to be native support by modern browsers.

In the previous article {% link _posts/2014-02-09-sortable-table-with-polymer-web-components.md %} a simple and concise sortable table was constructed using Polymer. This article will expand its capabilities to support cell templates.

The code in this post references *Polymer 0.1.4*, and may change in subsequent releases.

Adding cell templates to the sortable table, we have the requirements:
- JSON input,
- Columns can be statically configured (renamed, reordered, and hidden),
- Rows can be sorted by clicking on the column headers, and
- *Cells can be formatted by specifying a* `Template`.

The new feature doesn’t require a change to the original `simple-sortable-table` interface, with the exception that the `columns` object will now take an optional `cellTemplate` property. Ideally templates can be used to completely replace the `<td></td>` element and be configured simply by passing its `id` as a string.

Recall the `simple-sortable-table` component, the new `templated-sortable-table` component will have an identical structure:

```html
<polymer-element name="templated-sortable-table"
  attributes="data columns sortColumn sortDescending">
 
  <template>
    <table>
      <tr>
        <!--template to create column headers-->
      </tr>
      <!--template to create rows-->
        <!--nested template to create cells-->
          <!--new template to render cell-->
    </table>
  </template>
 
  <script>
    Polymer('templated-sortable-table', {
      data: [],
      columns: [],
      sortColumn: null,
      sortDescending: false
      <!--additional logic-->
    });
  </script>
</polymer-element>
```

The column headers do not require any change, and the basic structure of the row and cell iteration templates remains the same. What is new is the ability to dynamically specify a template to use to render each cell: either the column has specified a template for use or the default will be applied. This turns out to be rather easy as [Polymer Templates](http://www.polymer-project.org/platform/template.html) already support a `ref` attribute for this purpose. Simply binding the `ref` attribute to an `id` of another template will substitute its value, if `ref` is empty than the original content will be used.

```html
<template ref="{{-"{{"-}}column.cellTemplate}}" bind="{{-"{{"-}}templateData}}">
  <td>{{-"{{"-}}value}}</td>
</template>
```

While quite harmless looking, there are a few mysteries with the code. First, what are `{{-"{{"-}}templateData}}` and `{{-"{{"-}}value}}`, and where do they come from? Another issue arises due to a feature of Web Components: the [Shadow DOM](http://www.w3.org/TR/components-intro/#shadow-dom-section). Simply specifying a template in the parent document doesn’t make it accessible within the component. This is solved simple enough, it is possible to copy an element from the parent document into a component using Javascript – in our case this task should be performed whenever the `columns` array is modified.

```js
var self = this;
function addTemplateIfNotInDocument(templateId){
  if(templateId && !self.shadowRoot.getElementById(templateId)){
    var t = self.ownerDocument.getElementById(templateId);
    if(t!=null){
      self.shadowRoot.appendChild(t);
    }
  }
}
for(var i=0;i<this.columns.length;i++){
  addTemplateIfNotInDocument(this.columns[i].cellTemplate);
}
```

Aside: A small work-around to what may or may not be a bug in polymer has been omitted for clarity.

For any custom template to be useful it must have access to any appropriate variables. To render a cell, the template would require both the `{{-"{{"-}}value}}` of the cell as well as the cell’s `{{-"{{"-}}row}}` data. Looking at the template it appears bound to `{{-"{{"-}}templateData}}`, thus it must be an object containing all desired fields as properties:

```js
templateData = { row: row, value: value }
```

The easiest mechanism to create such an object is a Polymer filter (let’s call it `computeTemplateData`). The `computeTemplateData` filter must return the `templateData` object, so it must take both the `row` and `column` as inputs.

```js
PolymerExpressions.prototype.computeTemplateData = function(row, column) {
  var value = row[column.name];
  return { row: row, value: value };
};
```

To apply a filter typically it is necessary to wrap the code within another template:

```html
<template bind="{{-"{{"-}}templateData in row | computeTemplateData(column)}}">
  <template ref="{{-"{{"-}}column.cellTemplate}}" bind="{{-"{{"-}}templateData}}">
    <td>{{-"{{"-}}value}}</td>
  </template>
</template>
```

Thus the code to generate cell contents is complete. The two unresolved variables `row` and `column` must obviously come from our two original templates repeating over the `data` and `columns` arrays. We have created a drop-in addition to the previous `simple-sorted-table` component.

To create a demo application, suppose that our cellTemplate is actually a template containing more templates which will be conditionally chosen based on the cell `{{-"{{"-}}value}}`:

```html
<template id="fruitsTemplate">
  <td>
    <template if="{{-"{{"-}}value=='apple'}}">
      <img ... />
    </template>
    <template if="{{-"{{"-}}value=='banana'}}">
      <img ... />
    </template>
    <template if="{{-"{{"-}}value=='grape'}}">
      <img ... />
    </template>
    <template if="{{-"{{"-}}value=='pear'}}">
      <img ... />
    </template>
    <template if="{{-"{{"-}}value=='strawberry'}}">
      <img ... />
    </template>
    {{-"{{"-}}value}}
  </td>
</template>
 
<template id="example" bind>
  <templated-sortable-table
    data="{{-"{{"-}}data}}" columns="{{-"{{"-}}columns}}"
    sortColumn="fruit" sortDescending="false">
  </templated-sortable-table>
</template>
```

Once the template is specified in the columns data,

```js
var fruits = [
  {fruit: 'apple', alice: 4, bill: 10, casey: 2 },
  {fruit: 'banana', alice: 0, bill: 4, casey: 0 },
  {fruit: 'grape', alice: 2, bill: 3, casey: 5 },
  {fruit: 'pear', alice: 4, bill: 2, casey: 8 },
  {fruit: 'strawberry', alice: 0, bill: 14, casey: 0 }
];
 
var columns = [
  {name:'fruit', title:'Fruit', cellTemplate: 'fruitsTemplate' },
  {name:'alice', title:'Alice' },
  {name:'bill', title:'Bill' },
  {name:'casey', title:'Casey' }
];
```

The fruit cell is rendered to include an image, yet remains sortable.

{% 
  include githubproject.html 
  name="Sortable-Table Web Component"
  url="https://github.com/stevenrskelton/sortable-table"
  description="An expanded version of this Web Component featuring cell formulas, footers, and more is available on GitHub!"
%}