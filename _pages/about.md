---
permalink: /about/
title: "About"
author_profile: false
---

Steven Skelton lives in Toronto, Canada and has a Bachelor of Mathematics from the University of Waterloo in Waterloo, Canada.
After graduating he was a consultant to large asset management companies in Canada, specializing in systems integration, account and trading calculations, and automation of workflows.

Today, as Principal Software Engineer at a global digital consulting company, he is working on web services powering a large ecommerce platform.

### Executive Summary

- 17 years in Internet software: 6 financial services, 8 e-commerce.
- Best tools for the platform: Scala, C# .Net, and Dart Flutter,
- Reactive: event-driven infrastructure and coding paradigms,
- Cloud Architect: N-Tier, SOA, REST, API design, sidecar, CAP, layer abstraction.
- R&D speialist: benefit analysis, feasibility, integration strategies, initial adoption timelines.
- Data expert: algorithms, caching, full-text search, serialization, optimization.
- Security: audits, SSO, OAuth, SAML, dual-hop impersonation, encryption, signed tokens.

### Github Repositories

<ul style="display:flex;flex-wrap:wrap;-webkit-flex-wrap:wrap;list-style:none;padding-inline-start:0px;">

{% assign list = site.github.public_repositories | sort: 'stargazers_count' | reverse %}
{% for repository in list %}
{% if repository.name == "flag-icon" or repository.name == "sortable-table" or repository.name == "d3-geomap" or repository.name == "ordered-columns" or repository.name == "transform-to-json" or repository.name == "Fish-UI" %}
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
  
</ul>
