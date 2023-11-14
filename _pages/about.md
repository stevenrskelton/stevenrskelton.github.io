---
permalink: /about/
title: "About"
author_profile: false
---

<div style="
  border: 1px solid #343434;
  background-image: url('/assets/images/about_full.jpg');
  background-size: cover;
  background-blend-mode: soft-light;
  background-color: #e9dcbe;
  background-position: center;
  border-radius: 4px;
  padding: 30px;
">
  <p>
    <strong>🇨🇦 Toronto, Canada</strong>
  </p>

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
    <strong>7 years</strong> software consulting in <strong>financial services</strong> and fintech;<br>
    including:
  </p> 
  <ul>
    <li><em>Gluskin Sheff + Associates</em></li>
    <li><em>Dundee Wealth Management</em></li>
    <li><em>Home Trust Company</em></li>
    <li><em>Point72 Asset Management</em></li>
    <li><em>PureFacts Financial Solutions</em></li>
  </ul>
  <br>

  <p>
    <strong>3 years</strong> shorter engagement consulting across a diverse range of e-commerce, 
    customer data aggregation and non-profit.
  </p>
</div>

## What makes a good Principal Engineer?

- **Communication**: technology evangelist, ways of working, skills development, inter-team cohesion.
- **Proactive**: architectural runway, enterprise best practices, industry awareness, maintaining product agility.
- **R&D**: benefit analysis, feasibility, integration strategies, adoption timelines and planning.
- **Security**: audits, integrations, end-to-end encryption, PII privacy enforcements.
- **Data**: API efficiency and cacheability, read-write asymmetry, low-latency and event-driven streams.
- **Cloud**: agnostic, scaling, resiliency, cost-mitigation, and 95th percentile mitigations.

# Trade Audit Android App

<a href="https://tradeaudit.app" target="_blank"><div style="
  float: left;
  text-align: center;
  border: 1px solid #343434;
  border-radius: 4px;
  padding: 20px;
  margin-right: 20px;
  background-color: #e9dcbe;
  font-size: small;
">
 <img src="/assets/images/tradeauditapp.png" title="Trade Audit Mobile App" style="width:200px" alt="Trade Audit"/><br/>
 https://tradeaudit.app
</div></a>

Trade Audit is an Android Mobile App  


Stock market speculation is at a record high. Individuals are following social media accounts trying
to navigate meme-stocks, HODLs, and the opacity of SPACs. There is no accountability, no oversight, and 
 no rules to what can be presented as knowledgeable advice on social media.

Trade Audit is a mobile application that creates accountability.<br style="clear:left">

Use Trade Audit to catalogue any stock advice found on the internet.  Stock trades and ideas can be tracked 
creating an immutable track record that the social media accounts refuse to provide.  


# Public GitHub Repositories

<p style="font-size:small;margin-left:10px;">(Polymer Web Components is a deprecated framework, as all meaningful functionality is now browser native.)</p>

<div style="display:flex;flex-wrap:wrap;-webkit-flex-wrap:wrap;list-style:none;padding-inline-start:0px;">

{% assign list = site.github.public_repositories | sort: 'stargazers_count' | reverse %}
{% for repository in list %}
{% if respository.fork != true and repository.stargazers_count > 2 %}
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
