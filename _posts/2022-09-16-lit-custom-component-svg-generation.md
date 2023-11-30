---
title: "Lit Custom Components for SVG Generation"
categories:
  - Javascript
tags:
  - Lit
  - WebComponents
---

SVG markup is very similar to HTML, and the [Lit Web Components](https://lit.dev/) library can be used to not only
generate HTML custom components, but also manipulate SVG in a similar way using Lit templates. Lit is a small 5kb
library that removes the boilerplate code of DOM generation, and is highly interoperable with all web frameworks since
it relies on browser native custom elements.

{% include table-of-contents.html height="200px" %}

# A simple SVG Pie Chart

SVG is a robust standard that can be used for anything from company logos to graphical artwork. It is text-based XML
markup, and because it is a text format and not binary data like static images it can be manipulated in the browser
using javascript; operations such as resizing, changing colors, adding/removing elements or modifying text can be done
based on user input or other external inputs.

The example we will use in this article is a simple pie chart, dynamically created based on numerical input percentages.
The final output will be similiar to this:

<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 2 2" style="transform: rotate(-90deg);height:100px;float:left;margin-right:20px;">
  <path d="M 1 0 A 1 1 0 0 1 -0.9510565162951535 0.3090169943749475 L 0 0" fill="#900C3F"/>
  <path d="M -0.9510565162951535 0.3090169943749475 A 1 1 0 0 1 0.4817536741017157 -0.8763066800438634 L 0 0" fill="#581845"/>
  <path d="M 0.4817536741017157 -0.8763066800438634 A 1 1 0 0 1 1 0 L 0 0" fill="#FF5733"/>
</svg>

The `M`, `A`, `L` commands in the path data field are _moveto_, _arc_, and _lineto_; details on how they are used is in
the [SVG documentation](https://www.w3.org/TR/SVG11/paths.html#PathDataEllipticalArcCommands) but beyond the scope of
this article. The computation is basic trigonomity and hopefully the code provides an adequite explaination in itself,
but the original calculation is covered by David Gilbertson in his
blog [A simple pie chart in SVG](https://medium.com/hackernoon/a-simple-pie-chart-in-svg-dbdd653b6936).

The `viewBox` and `style` specify the render size, unlike static images SVG don't have a default resolution.
<br style="clear:left"/>

```xml
<svg>
  <path d="M 1 0 A 1 1 0 0 1 -0.9510565162951535 0.3090169943749475 L 0 0" fill="#900C3F"/>
  <path d="M -0.9510565162951535 0.3090169943749475 A 1 1 0 0 1 0.4817536741017157 -0.8763066800438634 L 0 0" fill="#581845"/>
  <path d="M 0.4817536741017157 -0.8763066800438634 A 1 1 0 0 1 1 0 L 0 0" fill="#FF5733"/>
</svg>
```

# Comparison of DOM manipultion and Lit Templates

Let's assume we have a dynamic data specifying a variable number of slices with their percent and color:

```javascript
[
  { "percent": 0.38, "color": "#900C3F" },
  { "percent": 0.45, "color": "#581845" },
  { "percent": 0.17, "color": "#FF5733" }
]
```

This data should be associated with the DOM element rendering rather than in global scope. In most cases, the best way
to achieve that is using a HTML attribute, so that is how we will structure both examples.

The first step in the rendering process will be to convert the slice data percent into the `d` value used by SVG Path
elements. For this we will use a `computePathData` function and a helper function, and these 2 functions will be common
to both implementations:

```javascript
function getCoordinatesForPercent(percent) {
  const x = Math.cos(2 * Math.PI * percent);
  const y = Math.sin(2 * Math.PI * percent);
  return [x, y];
}

function computePathData(slices){
  let cumulativePercent = 0;

  return slices.map(slice => {
    // destructuring assignment sets the two variables at once
    const [startX, startY] = getCoordinatesForPercent(cumulativePercent);

    // each slice starts where the last slice ended, so keep a cumulative percent
    cumulativePercent += slice.percent;

    const [endX, endY] = getCoordinatesForPercent(cumulativePercent);

    // if the slice is more than 50%, take the large arc (the long way around)
    const largeArcFlag = slice.percent > .5 ? 1 : 0;

      // create an array and join it just for code readability
    const pathData = [
      `M ${startX} ${startY}`, // Move
      `A 1 1 0 ${largeArcFlag} 1 ${endX} ${endY}`, // Arc
      `L 0 0`, // Line
    ].join(' ');

    return { d: pathData, fill: slice.color, };
  });
}
```

## Direct DOM Manipulation

The raw Javascript approach are straight-forward DOM operations called from a `pieChart` function:

```html
<svg id="piechart" viewBox="..." style="..."/>

<script>
  function pieChart(id){
    function getCoordinatesForPercent(percent) { ... }
    function computePathData(slices){ ... }

    var svgElement = document.getElementById(id);
    var attributeContent = svgElement.getAttribute('data-myattr');
    var slices = JSON.parse(attributeContent);
    var data = computePathData(slices);
    data.forEach(slice => {
      var pathElement = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      pathElement.setAttribute('d', slice.d);
      pathElement.setAttribute('fill', slice.fill);
      svgElement.appendChild(pathElement);
    });
  }
  pieChart('piechart', slices);
</script>
```

## Lit Templates

The Lit approach defines a custom element object rather than a function to encapsulate the rendering code:

```html
<script type="module">
  import {LitElement, html, svg} from 'https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js';

  export class PieChart extends LitElement {
    static properties = {
      slices: { type: Array },
    };

    getCoordinatesForPercent(percent) { ... }
    computePathData(slices){ ... }

    render() {
      let data = this.computePathData(this.slices);
      return svg`
        <svg viewBox="..." style="...">
          ${data.map(slice => svg`<path d="${slice.d}" fill="${slice.fill}"/>`)}
        </svg>`;
    }
  }
  customElements.define('pie-chart', PieChart);
</script>

<pie-chart slices='[
  { "percent": 0.38, "color": "#900C3F" },
  { "percent": 0.45, "color": "#581845" },
  { "percent": 0.17, "color": "#FF5733" }
]'/>
```

# Conclusion

The 2 code implementations are very similar. The custom element `<pie-chart>` is nice and the `svg` render engine in Lit
is slick.

Another major benefit of using Lit in this example is that there is no need to use element ids. The direct DOM
implementation requires each use to have a unique id (which can in some contexts be hard to ensure), and to call
the `pieChart(id)` function on both page load and any subsequent data change. When using multiple instances of a
generated element, a custom element approach will lead to more maintainable code and less brittle javascript.  
