---
permalink: /about/
title: "About"
author_profile: false
---

Steven Skelton lives in Toronto, Canada and has a Bachelor of Mathematics from the University of Waterloo in Waterloo, Canada.
After graduating he was a consultant to large asset management companies in Canada, specializing in systems integration, account and trading calculations, and automation of workflows.

Today, he is a Principal Software Engineer at a global digital consulting company.

## Executive Summary

- **Experience**:
  - **6 years financial services** consulting for Bay Street companies such as Gluskin Sheff + Associates, Dundee Wealth Management, and Home Trust.
  - **8 years e-commerce** for Walmart Canada, leading technical development of the website and API for mobile clients.
- **Communication**: technology evangelist, ways of working, and skills development.
- **Proactive**: architectural runway, enterprise best practices, industry awareness.
- **Cloud**: scaling, resilient, secure, CAP and 95th percentile.
- **R&D**: benefit analysis, feasibility, integration strategies, adoption timelines.
- **Data**: algorithms, caching, serialization, read-write asymmetry, low latency streams.
- **Security**: audits, integrations, encryption management.

## TradeAudit Mobile App

TradeAudit is a mobile app for iOS/Android targeting anyone using Twitter for stock trading ideas or recommendations. This financial side of Twitter is colloquially referred to as “fintwit”, _ie: Financial Twitter_, and is popular with a range of people from individuals managing their own money to hustlers trying to get rich quickly.

Twitter supports a feature called “cashtags” where users can represent stock ticker symbols within Tweets using a dollar sign, for example `$spy` represents the `SPDR S&P 500 ETF`. The TradeAudit mobile app allows Tweets made about stocks (with or without cashtags) to be recorded, tracked via live stock exchange prices, and aggregated to create historical performance records for Twitter accounts.

<p style="font-size:larger;font-weight:bold;text-align:center;">
  See <a href="https://tradeaudit.app" target="_blank" title="TradeAudit Mobile App">https://tradeaudit.app</a> for more
</p>

While the app is a closed-source, I have tried to create meaningful documentation around the technology, design, and business environment TradeAudit will operate in on this blog using the [tradeaudit tag](https://www.stevenskelton.ca/tags/#tradeaudit).

## Public Github Repositories

<p style="font-size:small;margin-left:10px;">(Polymer Web Components is a deprecated framework, as all meaningful functionality is now Browser native.)</p>

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
