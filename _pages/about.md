---
permalink: /about/
title: "About"
author_profile: false
---

<div style="float:left">
  <img src="/assets/images/about_full.jpg" alt="Steven Skelton">
</div>

**🇨🇦 Toronto, Canada**

**Honours Bachelor of Mathematics**  
Double major in Pure and Applied Mathematics from the University of Waterloo

**Principal Engineer**  
Currently at a global digital-transformation consulting company
<br style="clear:left">

## Work Summary

- **8 years e-commerce** for Walmart Canada, leading technical development of the e-commerce website and API for mobile clients,
with key successes in cloud re-platforming, continuous delivery and internal/3rd-party integrations.

- **7 years** software consulting in **financial services**, portfolio and asset management, and fintech;  
including Gluskin Sheff + Associates, Dundee Wealth Management, Home Trust, Point72 and PureFacts Financial.

- **3 years** shorter engagement consulting across a diverse range of e-commerce, customer data aggregation and non-profit. 

## What makes a good Principal Engineer?

- **Communication**: technology evangelist, ways of working, skills development, inter-team cohesion.
- **Proactive**: architectural runway, enterprise best practices, industry awareness, maintaining product agility.
- **R&D**: benefit analysis, feasibility, integration strategies, adoption timelines and planning.
- **Security**: audits, integrations, end-to-end encryption, PII privacy enforcements.
- **Data**: API efficiency and cacheability, read-write asymmetry, low-latency and event-driven streams.
- **Cloud**: agnostic, scaling, resiliency, cost-mitigation, and 95th percentile mitigations.

# Trade Audit Android App

<div style="float:left">
 <img src="/assets/images/tradeaudit.png" title="Trade Audit Mobile App" style="width:80px" alt="Trade Audit"><br/>
 <a href="https://tradeaudit.app" target="_blank">https://tradeaudit.app</a>
</div>

Trade Audit is an Android Mobile App  


Stock market speculation is at a record high. Individuals are following social media accounts trying
to navigate meme-stocks, HODLs, and the opacity of SPACs. There is no accountability, no oversight, and 
 no rules to what can be presented as knowledgeable advice on social media.

Trade Audit is a mobile application that creates accountability.

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
