---
#layout: post
title: "Advanced Uses of Polymer Templates"
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

<script src="/assets/images/2014/02/platform-0.1.4.js"></script>
<script src="/assets/images/2014/02/polymer-0.1.4.js"></script>
<link rel="import" href="/assets/images/2014/02/templated-sortable-table.html">

<template id="fruitsTemplate">
  <td>
    <template if="{{-"{{"-}}value=='apple'}}">
        <img alt="apple" style="vertical-align:middle" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAC4jAAAuIwF4pT92AAAEGklEQVRYw+2XW2xUVRSGv73Pbc7MtNhpy5BeQLBFQ6mFEggSLhKFECLIK+HBB8QL+mCiT2o0mJgYCA/GIAnRFxMCEmnUmCjVaGIMIH2RomgEisAAghQovc2cy14+UBCw7UwxgZiwHs8+e6///Gut/99HiQh3MzR3Oe4B+P8C6OvrVsCk3zq+WwF4t3uOfbsb0+lKb9tra/b3X76cfmj24hqgcKdLkG94+JEOpTF3ogQayAC1wFSgEmBy09xt2nINcE1MxgGNQ+/5/7UEqqezc0bvwV+e6u3oWNZ/5NhEExbcuOeyZWeqBstnzzpYsWjBdj9d1gc0n9iy9aXur75elj9/ttx2/dirr7+YXjj/+5olj232H5iyb8QkIyihndu9+90TG955Pi4ESovGOBZE4c2bgQOTYjP/z3RUKAy6156LKNxxZXh1WXq6ctL01qvvjX9y5ctAVBKAKz/sf6Pz2Rc2xFGIpW1EKSSKrma8JYyK0aK5tmjCAPf+SSQqs2RXr+LCp18w8NMhmfHZx+v8yZM/LAVA9sDjy7vyJ3NJHAelNBIGoxdSBJQCEey6GnQYkZo7k+ZNmwD48YlVJNzEXy1tOxuBnlGb8EL7N02DJ3NJZdlY2iqeHLCSKTAKpRSO5xN0d1O3dt319embN9Jz6NfqS3v3NRWdgoEjx2qtZBIRwQRBSSMSDfSjfA8TwWBXF1II6G5vv6FOBiMx/T8fnlgUgLi2JfkA23UpxagVCiuRgDBC+y4KjXYczmz9gDiOATj8+ptopTFBWFV0DP1xmfMmDokHI2zfxxQKN4z5cPxbEBvERGCARAJVCMHEfFtVjzu+Akt5CIKyrb6iDJQtnJOzPA+tFSY/iE4mRqs+EseYG8cznwfHQiLwxlejsDESowA7W322KAB/Qu3RiubpZ2Sos83A4L9pVwptu4iYYdkxQYCdcFDyz9zaflKqli49XooUD1Q9t/YjpdTIuux6gKCUjNAXEEcheC5oC0Qob5mec1LJkyV5Qaa56X2vKtM3/Ok2cRDcTPtI8hAUsBwHLIe69U9vB/IlAXAymVzNmtW7GOpihojWtovEIUjpBhiHIX5tdsBvaNg6FjeU+vXPvHLf/HlHMQaFwk76SBShtCo5uSh91UaXLfncq64+NRYzutrQfxxf0LnuxS8LZ86nlDEYE43B6C0s28ZvnHK6tW3nLODcmAEASBzN61i6si2fO529rvnD+cBNp1pYnotO2EHrJztXevV1e277QqIse+/Mth0tUze+vSXd2NDjpJIoDSKCk/Il8+iiE5ZjI8aAyJASWqSnPZhr2bVj8WjJS2LgligHKq4c6qyMui8m062t59zy8lO9vx+bdnFP+xwJw5Rdlr7kTZhwunrF8r1Ab9EPvPdrdg/A3QbwN3RVn5GHaM5pAAAAAElFTkSuQmCC" />
    </template>
    <template if="{{-"{{"-}}value=='banana'}}">
        <img alt="banana" style="vertical-align:middle" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAC4jAAAuIwF4pT92AAAD8klEQVRYw+2XW2xUVRiFv73POTPt9EYdeqEtYClFkHjBxEs0iGIgKMYYDMbESyJ9AJ/UBCVNhBJMNPigxnhLn4pAjGB80Bgj3h40IRIaAWNxSmlKK6UF67RzpnM9Z/8+lGmphXohYx9gJ+flXLLW/v/9r7WOEhFmcmlmeF2RBBRQPmME3OTZirYfNh4WY66fEQKDsZOP9A4eqY9nRhwAROT/vAp3fb+p++CJvbtz9+x871ggIOlPnsU70KRVvDDm9sy+8badzRMv5HnXfmr/Nj9aL/4f9eINLRDj3po1ft+9uecqr0LkHX3cxB/bg6QRJWhCiCTAWdWhi1uXAZl8HsJaf/SF15EUImoCHIBsYKw7+ZuCQpN4aY8ykUpBY1k5cDUGr1YcBrJ5I2D89udJ710haCxdhDHxCXDPEW2v+ShvQiTeyVUS27BDMEoTwhh3HNz3hc7OmgFtl3yTLwJLTWLzLsS1tCpGmCi7tiES8Zm/eOv7llXkTiIgMNt42cqUEIL/rA3z/Pi6z7T/8xxllSGSHAdXCn45agiE7uwqDd/32iQz8jLpRXu2P3n0nece6LWHV/b68WcOQ/quf9n1W/yRtQfxfqqHIMjweHG1hshxi2zGoXZRy4tA6sIvrQbVExHpqHq6ud+2VDSkzIlqyex/SrwjK1RgbRfQNw1ysZ/5eCtuUyvmVFirIEIaUCgUOuDQddwQH/WoW/LyW7PCK9+YYo1f797Yvfz+r+ptbXIThCKDkAVVCvYdx9ANXyp72Y/KvnlATEzwu+biH1opfsdqvEPXChZaBSeNmtaKzk6Iu0J4zvrv5i/Z+SCQmEJg+OyBVyLtm5oXLxVKSmyMpFBTjoF/XjcKAQOSHmssFoLC0qHxUVMKsh50HBMMQln4nvaGm9qWA8mLhgMRCfZFtn06dGbf6qLSFAsXaKZXZyenZghmTOEuaGt81ND5K2glVNRt+HbudS0PA+4l08l5Lyg43fnqBwN9760PBB3qag3lFQqTvVSkcRBlofARyaIU+Ebo7oJYTLAsm5qG5ner5jVtAeLTxqMLzEj/fmZfy0D3m5sTo7+FiossqmsUpWWCbWvEjA2sCKA0qEK0ShN300SjFv19ghMwhEpvOFW3cOuO4vLb28b69Tf57CJu2NjTseXt2LnP7/b8ZIHvQTDkURkWnAIL24ZspoBEMsm5AQetLdCeBOyKaHVjc1u46qHt05X8nxAA0OlE77x0pv+J6OkPH3WH2xuymbMhQwaFjYiP1g4FwTq3tGJNe7h2XavjlH9h2eGRnMtdLoG/Judr0qnBakkOFWX8/qBtlSWDxY2u5cwaBKKXFZGv/hldJXDFE/gTOHhqyah2ftUAAAAASUVORK5CYII=" />
    </template>
    <template if="{{-"{{"-}}value=='grape'}}">
        <img alt="grape" style="vertical-align:middle" src="data:image/png;base64,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" />
    </template>
    <template if="{{-"{{"-}}value=='pear'}}">
        <img alt="pear" style="vertical-align:middle" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAC4jAAAuIwF4pT92AAAAB3RJTUUH3gIMFDMjmdUkyQAAAB1pVFh0Q29tbWVudAAAAAAAQ3JlYXRlZCB3aXRoIEdJTVBkLmUHAAAEUUlEQVRYw8WXS2xUVRjHf+fce+fZTqfM9AFDGyAFkYdEQEuQaGLUqLgRBBMjYkw0YGJiDCbqwrARY6IsjDEu0AURDfHBAoxR6wIiEqGK5WGBtkgrzLQzfcyTmblz7z0uaAWEWodO4azv/b7f9/if7ztCKcWtPPpkDdglsz6ZON8odXeqtj7SW+7/crIAp460bfnkjSc7ot3HN93I/5MGiMxb2unxB1i48uE9twQgGG5sm7Ww9RzQfisAwp3ptidEk+6cGPrmeSBYrgExCRVEdp94+efzmd+alZAIB2ZPa+1eM/+dlUBiyjNwoPfD9/tSR5pBogkDRzj0jPzU8sv5T9+8GSW4r6N/7xqvUYMhdYI+H3e3LMRteDmW2PsC0DKlAN92vbW5YKcxnQJSGYQCQXxuD61zFpDInnO1x77cOJUA/li2835delHKpKjypLMXQSh0l44UOt3DBx6ZspuwYOYfy1nJOqUsQAMUsVQ/uZ4CuUIGQ9NJFqLzgQZgoNIZkIeiOzYVrQyKy+qRQpLNp5FCAyBnDvnT+YG1FS+BZeXDfyS+Xy7GmwuOBYCjShyO7Vr/f+yXBfBnuv2ugp31T2hUSLqGDy0BAhUFiGdPb0Y5YqLvFKAoBmKZzlUVA0gX4g+0x75YPbFzhVv6yReH5eHoru2AtyIA+/s+2OooGyH+OwE+I0DRzoDUODO4f+7vA1+9OGmAWObUK2eGDtzjqBJSuMeBUHiNWgpmGjGqBiklHf37XgPqbxig5BTC+7q2vq5wRjs9j5QuhJBXpd2lVWFZKZoaZjCnMcKMUB2WYxPPdYe/63l75+ilUT7AX8mjr6aL/WHBWNQC2y4gpYEQoNSlmocDbu5dvJxZddOZGWpg7vRmVi9bRcBXRWfix4cSue61NwLgP3jh42fHor9iio9CeKhyh6j2a7REZlOyrctZUYq8WWTFbYvJmzmxv++jbYCrXIBgqjgQuhw9VzlA2RSsLDX+IIyzV9i2Q6g6SDzT1QyEygVw2ZapXa1wkFIj7J6HZZvYjkkyl0XXtOvLUilGLmZwhCXg2kgmAkj5XMEsgCF9eLRaUBpVsoESeZCXShNPDnKu/wJCCKS4lC8pBLbjcLy3G11Kaj0zk0C63Gk43Bp5es8PZ9/bIJWOJtzUeyOMmL2UrBRitLF1TeNsPEpPPErIHyBQXUViZIRsMY8AhDBY1rj2ayBb9k5oq1Lzjl+fOqZJrSaVH8RteDDtHNc25hVFUuqfu0Iph5C/aeiZO3Yu0oTeX7YMNWH0Pb5o23MeGcz4jVqKdmpc54wWecy5o2ymeZtHNi7atf56zsvdipfuPvHSZ9HsyXmOssR1+ulfE1F3ZgaWnFy3YPsGoKNSa7k7XRx49HD08y2nB9tW5K20HJOfAoSQePUa+/a6Bw8un77u3YC78TvAnIp3gQAivcmjTUPFszNKVt5j6N6Ldb45A03Vd/YCF27Gw6Qi528YLNMa8NbmHQAAAABJRU5ErkJggg==" />
    </template>
    <template if="{{-"{{"-}}value=='strawberry'}}">
        <img alt="strawberry" style="vertical-align:middle" src="data:image/png;base64,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" />
    </template>
    {{-"{{"-}}value}}
  </td>
</template>

<template id="example" bind>
  <templated-sortable-table data="{{-"{{"-}}data}}" columns="{{-"{{"-}}columns}}" sortColumn="fruit" sortDescending="false"></templated-sortable-table>
</template>

<script>
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

window.addEventListener('polymer-ready', function(){
  var model = {
    data: fruits,
    columns: columns
  };
  document.getElementById('example').model = model;
});
</script>

{%
  include downloadsources.html
  src="/assets/images/2014/02/advanced-table-features-using-polymer-templates.html,/assets/images/2014/02/templated-sortable-table.html"
%}

{% 
  include githubproject.html 
  name="Sortable-Table Web Component"
  url="https://github.com/stevenrskelton/sortable-table"
  description="An expanded version of this Web Component featuring cell formulas, footers, and more is available on GitHub!"
%}