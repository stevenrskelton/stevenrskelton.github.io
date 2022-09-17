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

The `M`, `A`, `L` commands in the path data field are _moveto_, _arc_, and _lineto_; details on how they are used is in the [SVG documentation](https://www.w3.org/TR/SVG11/paths.html#PathDataEllipticalArcCommands) but beyond the scope of this article.  The computation is actually quite simple Javascript using basic trigonomity and hopefully the code provides an adequite explaination in itself.
<br style="clear:left"/>

```xml
<svg>
  <path d="M 1 0 A 1 1 0 0 1 -0.9510565162951535 0.3090169943749475 L 0 0" fill="#900C3F"/>
  <path d="M -0.9510565162951535 0.3090169943749475 A 1 1 0 0 1 0.4817536741017157 -0.8763066800438634 L 0 0" fill="#581845"/>
  <path d="M 0.4817536741017157 -0.8763066800438634 A 1 1 0 0 1 1 0 L 0 0" fill="#FF5733"/>
</svg>
```

## Comparison of DOM manipultion and Lit Templates

We'll go over generating the values a little later, but assuming we have the data:

```javascript
//given the data
const slices = [
  { d: "M 1 0 A 1 1 0 0 1 -0.9510565162951535 0.3090169943749475 L 0 0", fill:"#900C3F" },
  { d: "M -0.9510565162951535 0.3090169943749475 A 1 1 0 0 1 0.4817536741017157 -0.8763066800438634 L 0 0", fill: "#581845" },
  { d: "M 0.4817536741017157 -0.8763066800438634 A 1 1 0 0 1 1 0 L 0 0", fill: "#FF5733" },
];
```

### Rendering

#### DOM Manipulation

The raw Javascript approach is straight-forward but verbose.

```javascript 
//find the <svg> node in the page
const svgElement = document.querySelector('svg');
slices.forEach(slice =>
  const pathElement = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  pathElement.setAttribute('d', slice.d);
  pathElement.setAttribute('fill', slice.fill);
  svgElement.appendChild(pathElement);
);
```

#### Lit Templates

```javascript
return svg`<svg>
  ${slices.map(slice => svg`<path d="${slice.d}" fill="${slice.fill}"/>`}
</svg>`;
```

### Framework

The Lit Templates look better, but how much better?  While Lit is a small 5kb library, does this make it worth it?



