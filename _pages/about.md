---
permalink: /about/
title: "About"
author_profile: false
---

Steven Skelton lives in Toronto, Canada and has a Bachelor of Mathematics from the University of Waterloo in Waterloo, Canada.
After graduating he was a consultant to large asset management companies in Canada, specializing in systems integration, account and trading calculations, and automation of workflows.

Today, as Principal Software Engineer at a global digital consulting company, he is working on web services powering a large ecommerce platform.

### Executive Summary

- **Experience**: 16 total; 6 years financial services, 8 years e-commerce.
- **Communication**: technology evangelist, ways of working, project direction.
- **Proactive**: architectural runway, enterprise best practices, industry awareness.
- **Cloud**: scaling, resilient, secure, CAP and 95th percentile.
- **R&D**: benefit analysis, feasibility, integration strategies, adoption timelines.
- **Data**: algorithms, caching, serialization, read-write asymmetry, low latency streams.
- **Security**: audits, SSO, OAuth, SAML, impersonation, encryption.

### Github Repositories

<div style="display:flex;flex-wrap:wrap;-webkit-flex-wrap:wrap;list-style:none;padding-inline-start:0px;">

{% assign list = site.github.public_repositories | sort: 'stargazers_count' | reverse %}
{% for repository in list %}
{% if respository.fork != true and repository.stargazers_count != 0 %}
{{ repository }}
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
