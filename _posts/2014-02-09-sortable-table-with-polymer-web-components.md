---
#layout: post
title: "Sortable Table with Polymer Web Components"
categories:
  - Polymer
---

![Web Components](/assets/images/2014/02/webcomponents.png) As businesses now rely more heavily on web applications to perform daily operations, a user friendly datatable/spreadsheet is indispensable to all web developers. While individual requirements vary, the core staple is the sortable table. Using Polymer’s Templates and Data-Binding, one can be implemented in a remarkably concise way.

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

<script src="/assets/images/2014/02/platform-0.1.4.js"></script>
<script src="/assets/images/2014/02/polymer-0.1.4.js"></script>
<link rel="import" href="/assets/images/2014/02/simple-sortable-table.html">
<template id="tableTemplate" bind>
  <simple-sortable-table id="ssTable" data="{{-"{{"-}}data}}" columns="{{-"{{"-}}columns}}" sortColumn="p50" sortDescending="false"></simple-sortable-table>
</template>
<script>
  var ostrichMetrics = { 
    "getQuery_01_msec" : {
        "average" : 388,
        "count" : 4,
        "maximum" : 1283,
        "minimum" : 95,
        "p50" : 105,
        "p90" : 1283,
        "p95" : 1283,
        "p99" : 1283,
        "p999" : 1283,
        "p9999" : 1283,
        "sum" : 1553
    },
    "getQuery_02_msec" : {
        "average" : 8739,
        "count" : 12,
        "maximum" : 31568,
        "minimum" : 576,
        "p50" : 2858,
        "p90" : 31568,
        "p95" : 31568,
        "p99" : 31568,
        "p999" : 31568,
        "p9999" : 31568,
        "sum" : 104876
    },
    "getQuery_03_msec" : {
        "average" : 502,
        "count" : 1,
        "maximum" : 521,
        "minimum" : 521,
        "p50" : 521,
        "p90" : 521,
        "p95" : 521,
        "p99" : 521,
        "p999" : 521,
        "p9999" : 521,
        "sum" : 502
    },
    "getQuery_04_msec" : {
        "average" : 7492,
        "count" : 6,
        "maximum" : 19138,
        "minimum" : 1051,
        "p50" : 3491,
        "p90" : 19138,
        "p95" : 19138,
        "p99" : 19138,
        "p999" : 19138,
        "p9999" : 19138,
        "sum" : 44955
    },
    "getQuery_05_msec" : {
        "average" : 6583,
        "count" : 13,
        "maximum" : 19138,
        "minimum" : 950,
        "p50" : 5210,
        "p90" : 12825,
        "p95" : 19138,
        "p99" : 19138,
        "p999" : 19138,
        "p9999" : 19138,
        "sum" : 85587
    },
    "getQuery_06_msec"  : {
        "average" : 448,
        "count" : 4,
        "maximum" : 637,
        "minimum" : 212,
        "p50" : 349,
        "p90" : 637,
        "p95" : 637,
        "p99" : 637,
        "p999" : 637,
        "p9999" : 637,
        "sum" : 1793
    },
    "getQuery_07_msec" : {
        "average" : 287,
        "count" : 1,
        "maximum" : 286,
        "minimum" : 286,
        "p50" : 286,
        "p90" : 286,
        "p95" : 286,
        "p99" : 286,
        "p999" : 286,
        "p9999" : 286,
        "sum" : 287
    },
    "getQuery_08_msec" : {
        "average" : 138,
        "count" : 3,
        "maximum" : 173,
        "minimum" : 105,
        "p50" : 128,
        "p90" : 173,
        "p95" : 173,
        "p99" : 173,
        "p999" : 173,
        "p9999" : 173,
        "sum" : 416
    },
    "getQuery_09_msec" : {
        "average" : 310,
        "count" : 12,
        "maximum" : 3158,
        "minimum" : 26,
        "p50" : 35,
        "p90" : 116,
        "p95" : 3158,
        "p99" : 3158,
        "p999" : 3158,
        "p9999" : 3158,
        "sum" : 3728
    },
    "getQuery_10_msec" : {
        "average" : 1762,
        "count" : 5,
        "maximum" : 5210,
        "minimum" : 427,
        "p50" : 1051,
        "p90" : 5210,
        "p95" : 5210,
        "p99" : 5210,
        "p999" : 5210,
        "p9999" : 5210,
        "sum" : 8814
    },
    "getQuery_11_msec" : {
        "average" : 1640,
        "count" : 4,
        "maximum" : 4714,
        "minimum" : 316,
        "p50" : 704,
        "p90" : 4714,
        "p95" : 4714,
        "p99" : 4714,
        "p999" : 4714,
        "p9999" : 4714,
        "sum" : 6561
    },
    "getQuery_12_msec" : {
        "average" : 1019,
        "count" : 4,
        "maximum" : 2339,
        "minimum" : 286,
        "p50" : 472,
        "p90" : 2339,
        "p95" : 2339,
        "p99" : 2339,
        "p999" : 2339,
        "p9999" : 2339,
        "sum" : 4077
    },
    "getQuery_13_msec" : {
        "average" : 89,
        "count" : 1,
        "maximum" : 86,
        "minimum" : 86,
        "p50" : 86,
        "p90" : 86,
        "p95" : 86,
        "p99" : 86,
        "p999" : 86,
        "p9999" : 86,
        "sum" : 89
    },
    "getQuery_14_msec" : {
        "average" : 257,
        "count" : 12,
        "maximum" : 2858,
        "minimum" : 10,
        "p50" : 26,
        "p90" : 95,
        "p95" : 2858,
        "p99" : 2858,
        "p999" : 2858,
        "p9999" : 2858,
        "sum" : 3095
    },
    "getQuery_15_msec" : {
        "average" : 706,
        "count" : 1,
        "maximum" : 704,
        "minimum" : 704,
        "p50" : 704,
        "p90" : 704,
        "p95" : 704,
        "p99" : 704,
        "p999" : 704,
        "p9999" : 704,
        "sum" : 706
    }
  };
  var ostrichMetricsArray = []; 
  var names = Object.getOwnPropertyNames(ostrichMetrics);
  for (var i = 0; i < names.length; i++) {
    var name = names[i];
    var data = ostrichMetrics[name];
    data.name = name;
    ostrichMetricsArray.push(data);
  }

  function bindData(){
    var columns = null;
    if(window.innerWidth < 500){
      columns = [
        {name:'name', title:'service call'},
        {name:'average'},
        {name:'count'}
      ];
    }else if(window.innerWidth < 950){
      columns = [
        {name:'name', title:'service call'},
        {name:'average'},
        {name:'count'},
        {name:'maximum'},
        {name:'minimum'}
      ];
    }else{
      columns = [
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
    }
    document.getElementById('tableTemplate').model = {
      data: ostrichMetricsArray,
      columns: columns
    };	
  }      
 
  window.addEventListener('polymer-ready', bindData);
  window.addEventListener('resize', bindData);
</script>

{%
  include downloadsources.html
  src="/assets/images/2014/02/sortable-table-polymer-web-components.html,/assets/images/2014/02/simple-sortable-table.html"
%}
