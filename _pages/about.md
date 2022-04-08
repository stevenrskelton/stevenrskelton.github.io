---
permalink: /about/
title: "About"
author_profile: false
---

Steven Skelton has experience as a consultant to some of the largest asset management companies in Canada. Specializing in systems integration and the architecture of automated workflows, he has now turned his full attention to software technologies enabling modular, event driven and highly available software.

Today, as Principal Software Engineer at an international digital consulting company, he is working on the web services powering a large ecommerce platform.

### Experience Summary

- 17 years in Internet software: 6 financial services, 8 e-commerce.
- Best tools for the platform: Scala, C# .Net, and Dart,
- Reactive: event-driven infrastructure and coding paradigms,
- Cloud Architect: N-Tier, SOA, REST, API design, sidecar, CAP, layer abstraction.
- R&D specialist: benefit analysis, feasibility, integration strategies, initial adoption timelines.
- Data expert: algorithms, caching, full-text search, serialization, optimization.
- Security: audits, SSO, OAuth, SAML, dual-hop impersonation, encryption, signed tokens.

### Github Repositories

<ul style="display:flex;flex-wrap:wrap;-webkit-flex-wrap:wrap;list-style:none;padding-inline-start:20px;">

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
