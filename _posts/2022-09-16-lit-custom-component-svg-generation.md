---
title: "Lit Custom Components for SVG generation"
categories:
  - Javascript
tags:
  - Lit
---
SVG markup is very similar to HTML, and the [Lit Web Components](https://lit.dev/) library can be used to not only generate HTML custom components, but also manipulate SVG in a similar way using Lit templates. Lit is a small 5kb library that removes the boilerplate code of DOM generation, and is highly interporable with all web frameworks since it relies on browser native custom elements.

## A simple SVG Pie Chart

SVG is a robust standard that can be used for anything from company logos to graphical artwork. It is text-based XML markup, and because it is a text format and not binary data like static images it can be manipulated in the browser using javascript; operations such as resizing, changing colors, adding/removing elements or modifying text can be done based on user input or other external inputs.

The example we will use in this article is a simple pie chart, dynamically created based on numerical input percentages.  The final output will be similiar to this:

<svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 2 2" style="transform: rotate(-90deg);height:100px;float:left;margin-right:20px;">
  <path d="M 1 0 A 1 1 0 0 1 -0.9510565162951535 0.3090169943749475 L 0 0" fill="#900C3F"/>
  <path d="M -0.9510565162951535 0.3090169943749475 A 1 1 0 0 1 0.4817536741017157 -0.8763066800438634 L 0 0" fill="#581845"/>
  <path d="M 0.4817536741017157 -0.8763066800438634 A 1 1 0 0 1 1 0 L 0 0" fill="#FF5733"/>
</svg>

The `M`, `A`, `L` commands in the path data field are _moveto_, _arc_, and _lineto_; details on how they are used is in the [SVG documentation](https://www.w3.org/TR/SVG11/paths.html#PathDataEllipticalArcCommands) but beyond the scope of this article. The computation is basic trigonomity and hopefully the code provides an adequite explaination in itself, but the original calculation is covered by David Gilbertson in his blog [A simple pie chart in SVG](https://medium.com/hackernoon/a-simple-pie-chart-in-svg-dbdd653b6936).
<br style="clear:left"/>

```xml
<svg>
  <path d="M 1 0 A 1 1 0 0 1 -0.9510565162951535 0.3090169943749475 L 0 0" fill="#900C3F"/>
  <path d="M -0.9510565162951535 0.3090169943749475 A 1 1 0 0 1 0.4817536741017157 -0.8763066800438634 L 0 0" fill="#581845"/>
  <path d="M 0.4817536741017157 -0.8763066800438634 A 1 1 0 0 1 1 0 L 0 0" fill="#FF5733"/>
</svg>
```

## Comparison of DOM manipultion and Lit Templates

Let's assume we have a dynamic data input, specifying a varying number of slices with their percent and color:

```javascript
var slices = [
    { percent: 0.38, color: "#900C3F" },
    { percent: 0.45, color: "#581845" },
    { percent: 0.17, color: "#FF5733" },
]
```

### Rendering

The first step will be to convert the `slices` data percent to the `d` values used in SVG with a function `computePathData` and a helper:
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

#### Direct DOM Manipulation

The raw Javascript approach is straight-forward DOM operations.

```html
<svg id="pie" viewBox="-1 -1 2 2" style="transform: rotate(-90deg);height:200px;"/>
<script>
  function getCoordinatesForPercent(percent) { ... }
  function computePathData(slices){ ... }
  
  //find the <svg> node in the page
  var svgElement = document.getElementById('piechart');
  var data = computePathData(slices);
  data.forEach(slice => {
    var pathElement = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    pathElement.setAttribute('d', slice.d);
    pathElement.setAttribute('fill', slice.fill);
    svgElement.appendChild(pathElement);
  });
</script>
```

#### Lit Templates

```html
<script type="module">
        import {LitElement, html, svg} from 'https://cdn.jsdelivr.net/gh/lit/dist@2/core/lit-core.min.js';

        export class PieChart2 extends LitElement {
            static properties = {
                slices: { type: Array },
            };

           getCoordinatesForPercent(percent) {
              const x = Math.cos(2 * Math.PI * percent);
              const y = Math.sin(2 * Math.PI * percent);
              return [x, y];
            }

             computePathData(slices){
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

          render() {
            let data = computePathData(slices);
            return svg`
               <svg xmlns="http://www.w3.org/2000/svg" viewBox="-1 -1 2 2" style="transform: rotate(-90deg);height:200px;">
               ${data.map(slice => svg`<path d="${slice.d}" fill="${slice.fill}"/>`)}
            </svg>`;
          }
        }
        customElements.define('pie-chart2', PieChart2);
        </script>
pie-chart2
<pie-chart></pie-chart>

```
The Lit template 
```javascript
return svg`<svg>
  ${slices.map(slice => svg`<path d="${slice.d}" fill="${slice.fill}"/>`}
</svg>`;
```

### Framework

The Lit Templates look better, but how much better?  While Lit is a small 5kb library, does this make it worth it?



