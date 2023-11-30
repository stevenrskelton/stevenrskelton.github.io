---
permalink: /about/
title: "About"
author_profile: false
---
<div class="img-bg" style="background-image:url('/assets/images/about/bg.jpg');">
  <div style="margin-bottom: 0.5em;font-size: 28pt;">
    🇨🇦<div style="
      padding-left: 8px;
      display: inline-block;
      font-weight: bolder;
      font-size: 20pt;
      vertical-align: text-top;
      padding-top: 2pt;">Toronto, Canada</div>
  </div>

  <p>
    <strong>Honours Bachelor of Mathematics</strong><br>
    Double major in Pure and Applied Mathematics from the University of Waterloo
  </p>
  
  <p>
    <strong>Principal Engineer</strong><br>
    Currently at a global digital-transformation consulting company
  </p>
  
  <h2 id="work-summary">Work Summary</h2>

  <p>
    <strong>8 years e-commerce</strong> for Walmart Canada, leading technical development of the e-commerce website 
    and API for mobile clients, with key successes in cloud re-platforming, continuous delivery and internal/3rd-party integrations.
  </p>

  <p>
    <strong>7 years</strong> software consulting in <strong>financial services</strong> and fintech; 
    <wbr>including:
  </p> 
  <ul style="padding-bottom:0.5em">
    <li><em>Gluskin Sheff + Associates</em></li>
    <li><em>Dundee Wealth Management</em></li>
    <li><em>Home Trust Company</em></li>
    <li><em>Point72 Asset Management</em></li>
    <li><em>PureFacts Financial Solutions</em></li>
  </ul>

  <p>
    <strong>3 years</strong> shorter engagement consulting across a diverse range of e-commerce, 
    <wbr>customer data aggregation and non-for-profit.
  </p>
</div>

## Principal Skills

- **Communication**: technology evangelist, ways of working, skills development, inter-team cohesion.
- **Proactive**: architectural runway, enterprise best practices, industry awareness, maintaining product agility.
- **R&D**: benefit analysis, feasibility, integration strategies, adoption timelines and planning.
- **Security**: audits, integrations, end-to-end encryption, PII privacy enforcements.
- **Data**: API efficiency and cacheability, read-write asymmetry, low-latency and event-driven streams.
- **Cloud**: agnostic, scaling, resiliency, cost-mitigation, and 95th percentile mitigations.

<div class="img-bg" style="background-image:url('/assets/images/about/dancing_robots.jpg');margin-top: 50px;margin-bottom: 30px;">
  <h1>Trade Audit Android App</h1>
  
  <style>
    @media(min-width: 400px) {
      .img-fill {
        float: left;
        margin-right: 20px;
        margin-bottom: 4px;
      }
    }
  </style>
  <div class="img-fill" style="
    text-align: center;
    margin-bottom: 1em;
    font-size: small;
  ">
    <a href="https://tradeaudit.app" target="_blank" style="text-decoration: none;">
     <img src="/assets/images/about/tradeauditapp.png" title="Trade Audit Mobile App" 
      style="
        width: 160px;
        box-shadow: none;
        padding: 14px 20px 4px 20px;
      " alt="Trade Audit"/><br/>
     <span style="font-weight:bold;color:#1da1f2;">https://tradeaudit.app</span>
    </a><br>
    <a href="https://play.google.com/store/apps/details?id=app.tradeaudit" target="_blank"><img alt="Get it on Google Play" style="height:60px;box-shadow:none;" height="60" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"></a>
  </div>
  <p>
    Stock market speculation is at a record high. Individuals are following social media accounts trying
    to navigate meme-stocks, HODLs, and the opacity of SPACs. There is no accountability, no oversight, and 
    no rules to what can be presented as knowledgeable advice on social media.
  </p>
  <p>
    Trade Audit is a mobile application that creates accountability.
  </p>
  <p>
    Use Trade Audit to catalogue any stock advice found on the internet.  Stock trades and ideas can be tracked 
    creating an immutable track record that the social media accounts refuse to provide.
  </p>
  <div style="height:0;clear:left"></div>
</div>


## GitHub Repositories

### Public Projects

<div style="
  display: flex;
  flex-wrap: wrap;
  -webkit-flex-wrap: wrap;
  list-style: none;
  padding-inline-start: 0px;
  padding-top: 0.75em;
">

{% assign list = site.github.public_repositories | where: "archived", false | where: "fork", false | sort: 'updated_at' | reverse %}
{% for repository in list %}
{%
include github_repository.html
name=repository.name
homepage=repository.homepage
html_url=repository.html_url
description=repository.description
language=repository.language
stargazers_count=repository.stargazers_count
forks_count=repository.forks_count
%}
{% endfor %}

</div>

### Archived Polymer Web Components, Outdated JS and Scala
*Polymer Library was deprecated in 2019 when functionality became browser native, <wbr>remaining Polymer functionality became [Lit](https://lit.dev/)*

<div style="display:flex;flex-wrap:wrap;-webkit-flex-wrap:wrap;list-style:none;padding-inline-start:0px;">

{% assign list = site.github.public_repositories | where: "archived", true | where: "fork", false | sort: 'stargazers_count' | reverse %}
{% for repository in list %}
{% if repository.stargazers_count > 2 %}
{%
  include github_repository.html
  name=repository.name
  homepage=repository.homepage
  html_url=repository.html_url
  description=repository.description
  language=repository.language
  stargazers_count=repository.stargazers_count
  forks_count=repository.forks_count
%}
{% endif %}
{% endfor %}
  
</div>
