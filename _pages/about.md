---
permalink: /about/
title: "About"
author_profile: false
---

Steven Skelton lives in Toronto, Canada and has a Bachelor of Mathematics from the University of Waterloo in Waterloo, Canada.
Today, he is a Principal Software Engineer at a global digital-transformation consulting company.

## Executive Summary

- **Experience**:
  - **6 years financial services** consulting for Bay Street financial service companies including: Gluskin Sheff + Associates, Dundee Wealth Management, and Home Trust.
  - **8 years e-commerce** for Walmart Canada, leading technical development of the primary e-commerce website and API for mobile clients, instrumenting cloud replatforming, continuous delivery and internal/external integrations.
- **Communication**: technology evangelist, ways of working, skills development.
- **Proactive**: architectural runway, enterprise best practices, industry awareness.
- **R&D**: benefit analysis, feasibility, integration strategies, adoption timelines.
- **Security**: audits, integrations, end-to-end encryption.
- **Data**: API efficiency and cacheability, read-write asymmetry, low-latency streams.
- **Cloud**: scaling, resiliency, cost-mitigation, and 95th percentile.

## TradeAudit Mobile App

TradeAudit is a mobile app for iOS/Android targeting anyone using Twitter for stock trading ideas or investment recommendations. This financial side of Twitter is colloquially referred to as “fintwit”, _ie: Financial Twitter_, and is popular with a range of people from individuals managing their own money to hustlers trying to get rich quickly.

Twitter supports a feature called “cashtags” where users can represent stock ticker symbols within Tweets using a dollar sign, for example `$spy` represents the `SPDR S&P 500 ETF`. The TradeAudit mobile app allows Tweets made about stocks (with or without cashtags) to be recorded, tracked via live exchange prices, and aggregated to create historical performance records for Twitter accounts.

<p style="font-size:larger;font-weight:bold;text-align:center;">
  See <a href="https://tradeaudit.app" target="_blank" title="TradeAudit Mobile App">https://tradeaudit.app</a> for more
</p>

While the app is a closed-source, it is a meaningful source for documentation around the technology, design, and business environment TradeAudit will operate in.  All posts about TradeAudit on this blog will include the [tradeaudit](https://www.stevenskelton.ca/tags/#tradeaudit) tag.

## Public Github Repositories

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
