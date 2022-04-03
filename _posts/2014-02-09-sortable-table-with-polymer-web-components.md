---
#layout: post
title: "Sortable Table with Polymer Web Components"
categories:
  - Polymer
---

As businesses now rely more heavily on web applications to perform daily operations, a user friendly datatable/spreadsheet is indispensable to all web developers. While individual requirements vary, the core staple is the sortable table. Using Polymer’s Templates and Data-Binding, one can be implemented in a remarkably concise way.

The code in this post references *Polymer 0.1.4*, and may change in subsequent releases.

The sortable table has a few standard requirements:

- JSON input,
- Columns can be statically configured (renamed, reordered, and hidden), and
- Rows can be sorted by clicking on the column headers.

Consider a use-case of finding the maximum value contained within various columns: [Ostrich](https://github.com/twitter/ostrich) is a reporting library on the JVM that gathers performance statistics and query execution times, making them available as JSON. It is a realistic input, and a simple sortable table provides a measurable benefit the the user.

Using the Ostrich JSON dataset as reference, we should create another array to define the order, title, and any other properties for the columns we wish to be in the rendered table:

```js
var columns = [
  {name:'name', title:'service call'},
  {name:'average'},
  {name:'count'},
  {name:'maximum'},
  {name:'minimum'},
  {name:'p50'},
  {name:'p90'},
  {name:'p95'},
  {name:'p99'},
  {name:'p999'},
  {name:'p9999'},
  {name:'sum'}
];
```

In addition to being able to add column specific properties, by specifying the `columns` data separately from the input `data` we reduce restrictions on the `data` format. The `data` dataset can now contain arbitrary JSON elements, we will pick and choose what is displayed in our data table simply omitting missing or additional fields.

Web Components fully encapsulate all functionality behind an interface of HTML attributes. We have a specification for the `data` source, displayed `columns`, and we will also expose the currently sorted column and its direction. The custom element interface for our new _simple-sortable-table_ element is:

```html
<simple-sortable-table
  data="{{-"{{"-}}data}}"
  columns="{{-"{{"-}}columns}}"
  sortColumn="p50"
  sortDescending="false">
</simple-sortable-table>
```

The Polymer element definition mirrors the above by exposing the same set of attributes. The internal logic of the Web Component will take advantage of [HTML Templates](http://www.polymer-project.org/platform/template.html) to perform 2 tasks: create column headers, and create rows.

```html
<polymer-element name="simple-sortable-table"
  attributes="data columns sortColumn sortDescending">
 
  <template>
    <table>
      <tr>
        <!--TODO: template to create column headers-->
      </tr>
      <!--TODO: template to create rows-->
        <!--TODO: nested template to create cells-->
    </table>
  </template>
 
  <script>
    Polymer('simple-sortable-table', {
      data: [],
      columns: [],
      sortColumn: null,
      sortDescending: false
    });
  </script>
</polymer-element>
```

Both tasks will iterate over the `column` array, as both need to correctly filter and order the displayed columns. Starting on the first task, column headers require a few features, they must: capture click events, display sort status, and show a column title. Column header click events will be handled by a new function called `changeSort`, when a user clicks on a header it will be called, determine which column was clicked, then update the sort settings. Since the sort variables `sortColumn` and `sortDescending` are bound to the Polymer element, updating either will automatically re-render the entire table with the proper sort.

Because user-defined parameters cannot be sent to event handlers we cannot send `changeSort` the clicked column as an argument. However each event handler is passed the source DOM element, so as long as the source element was rendered using a Polymer template it will expose a `model` property containing a reference to its template’s bound data model. If the element’s template was bound using a `repeat`, the `model` property will be a reference to the specific item of the collection corresponding to this element, in our case the item in the `columns` array.

```js
changeSort: function(e,p,o){
  var clickedSortColumn = o.templateInstance_.model.column.name;
  if(clickedSortColumn == this.sortColumn){
    //column already sorted, reverse sort
    this.sortDescending = !this.sortDescending;
  }else{
    this.sortColumn = clickedSortColumn;
  }
}
```

Using overline and underline to indicate descending and ascending sorting respectively, the column header template is:

```html
<template repeat="{{-"{{"-}}column in columns}}">
  <th on-click="{{-"{{"-}}changeSort}}" style="{{-"{{"-}}
    sortColumn == column.name ? (
      sortDescending ? 
        'text-decoration:overline' 
        : 'text-decoration:underline'
    ) : ''
  }}">
    {{-"{{"-}}!(column.title) ? column.name : column.title}}
  </th>
</template>
```

The actual row sorting will be performed using a Polymer Filter applied to the row template data. For an introduction on Polymer Filters, I have written an article: {% link _posts/2014-02-06-polymer-data-binding-filter.md %}.

```js
PolymerExpressions.prototype.sortByKey = function(array, key, desc) {
  return array.sort(function(a, b) {
    var x = a[key];
    var y = b[key];
 
    if (typeof x == "string"){
      x = x.toLowerCase(); 
      y = y.toLowerCase();
    }
    if(desc){
      return ((x < y) ? 1 : ((x > y) ? -1 : 0));
    }else{
      return ((x < y) ? -1 : ((x > y) ? 1 : 0));
    }
  });
};
```

The `data` for the table is JSON meaning each row of data is a JSON object; we can reference a particular cell of the row by column name. This is much cleaner than dealing with a numerical index since the column’s displayed order may be different than its order within the row data.

```html
<template repeat="{{-"{{"-}}
  row in data | sortByKey(sortColumn, sortDescending)
}}">
  <tr>
    <template repeat="{{-"{{"-}}column in columns}}">
      <td>{{-"{{"-}}row[column.name]}}</td>
    </template>
  </tr>
</template>
```

We have defined all the code for a sortable table, the only task left is to style. Alternating row background color is both appealing and simple using the `nth-of-type` CSS selector, more advanced conditional formatting will hopefully be the subject of a future article – although it is quite straightforward to implement by adding an additional formatting function property to the `columns` array.

The following live-demo takes advantage of `columns` data binding and the `window.resize` event to show/hide the number of columns based on user screen resolution (resize your browser to try it out).

{%
  include downloadsources.html
  src="/assets/images/2014/02/sortable-table-polymer-web-components.html,/assets/images/2014/02/simple-sortable-table.html"
%}
