---
title: "Trade Audit Mobile App Infrastructure"
categories:
  - Platform
  - Scala
  - Dart
tags:
  - TradeAudit
  - AWS
  - GitHub
excerpt_separator: <!--more-->
---

The release of the [Trade Audit](https://tradeaudit.app) mobile app is almost here. It is currently in MVP stage, but
its infrastructure is a pretty typical cloud based deployment. This article discusses design choices made, evaluating
how effective they were.<!--more-->

{% include table-of-contents.html height="300px" %}

### Mobile App Requirements

Trade Audit is a mobile app targeting anyone using Twitter for stock trading ideas/recommendations.
This side of Twitter is colloquially referred to as "fintwit", ie: _Financial Twitter_.

Twitter has a feature called "cashtags" where users can represent stock ticker symbols within Tweets using a dollar
sign, for example `$spy` represents the `SPDR S&P 500 ETF`.
The mobile app allows Tweets made about stocks (with or without cashtags) to be recorded, tracked via price information,
and aggregated to create historical performance records for Twitter accounts.

High level, this requires:

- [Mobile App iOS/Android Client](#mobile-app-iosandroid-client)
- [Server-side API](#server-side-api) with both relational data representing trades made by Twitter accounts, and
  random-access stock pricing data
- [Support Website](#support-website) which can provide additional information about the app outside of the Apple App
  Store and Google Play Store pages

![Infrastructure](/assets/images/2022/10/tradeaudit-release-infrastructure.png)

## Mobile App iOS/Android Client

For developers not wanting a separate codebase for iOS and Android, there are 3 options:

| Framework    | Release |  Language  | Support                |
|:-------------|:-------:|:----------:|:-----------------------|
| Xamarin      |  2011   |     C#     | Microsoft since 2016   |
| React Native |  2015   | JavaScript | Indirectly by Facebook |
| Flutter      |  2017   |    Dart    | Google                 |

<table style="margin-left:auto;margin-right:auto;max-width:750px;display:table;">
  <thead>
    <tr>    
      <th>Framework</th>
      <th>Release</th>
      <th>Language</th>
      <th>Support</th>    
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Xamarin</td>
      <td>2011</td>
      <td>C#</td>
      <td>Microsoft since 2016</td>
    </tr>
    <tr>
      <td>React Native</td>
      <td>2015</td>
      <td>JavaScript</td>
      <td>Indirectly by Facebook</td>
    </tr>
    <tr>
      <td>Flutter</td>
      <td>2017</td>
      <td>Dart</td>
      <td>Google</td>
    </tr>
  </tbody>
</table>

The choice for your mobile app, if at all, depends on many factors. While Flutter lacks the maturity of Xamarin and the
larger ecosystem of React Native, it is arguably the most intuitive development environment. The risk is that without
Google's continued support it may not survive, but adoption continues to grow lessening this concern.

All solutions evaluated for client-server communication should offer self-documented contracts; preferably with code
generation and multi-language compatibility if applicable. Best practices to minimize maintenance is to ensure that
incompatible changes to server schema will immediately break client compatibility before runtime.

GraphQL is a flexible approach and a good choice in multi-consumer scenarios with varied workloads. However, the
quickest approach to get
up and running is [gRPC](https://grpc.io/) due to its clean abstraction of networking, making it transparent in code
that already has proper asynchronous or streaming support.

## Server-side API

The Trade Audit server-side hardware is a mix of non-cloud Virtual Private Servers (VPS) and serverless AWS. During
pre-adoption phase (for bootstrapping ventures; seed investment is different) the emphasis is on
economics while remaining flexible to scaling in the future. A monthly VPS server is cheaper than even a yearly
reserved instance in AWS, while being more financially flexible. For stateless services leaning on proper DevOps tools
including [Ansible](https://www.ansible.com/) swapping out on a VPS is trivially more complicated than even the most
streamlined AWS EC2 upgrade paths. The public cloud excels in (i) serverless and (ii) irregular loads, as pointed out
by large companies when they decide
to [leave the cloud](https://world.hey.com/dhh/why-we-re-leaving-the-cloud-654b47e0).

### gRPC API and website.

There is no one-size-fits-all language or framework for server implementations. Generally this is a good reason to
choose
a client-server interface with multi-language support. Even if features align initially to a single server-side
framework,
feature growth often is better served by expanding server-side languages or frameworks in the future, especially when
implementing under a microservice paradigm.

The initial server-side implementation chosen for Trade Audit is based around Akka-HTTP and Akka-Streams.

_It should be noted that the [Sept 7, 2002](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka)
license change will require paid licenses to use *future* Akka releases; this also won't apply to many no/low revenue
projects,
and not-upgrading will likely continue to be a popular and supported choice but should be evaluated._

### Databases

The data layer can really make or break an application. Trade Audit currently requires:

| Implementation | Type       | Host |
|----------------|------------|------|
| PostgreSQL     | Relational | VPS  |
| SQS            | Buffering  | AWS  |
| DynamoDB       | Key Value  | AWS  |

<table style="margin-left:auto;margin-right:auto;max-width:750px;display:table;">
  <thead>
    <tr>    
      <th>Implementation</th>
      <th>Type</th>
      <th>Host</th>  
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>PostgreSQL</td>
      <td>Relational</td>
      <td>VPS</td>
    </tr>
    <tr>
      <td>SQS</td>
      <td>Buffering</td>
      <td>AWS</td>
    </tr>
    <tr>
      <td>DynamoDB</td>
      <td>Key Value</td>
      <td>AWS</td>
    </tr>
  </tbody>
</table>

The AWS offerings for RDS seem to be the least compelling; both by cost and additional features over VPS. The primary
reason to use public cloud is rapid scalability, and outside of AWS Aurora it seems like the same amount of effort.

## Support Website

While a lot of mobile apps can get by without a separate website, there are many reasons to create one, including:

- flexibility not offered by Apple App Store and Google Play Store sites
- full web equivalent, or teasing a limited feature-set on the web
- avoiding 15-30% service fees for transactions within the app

In the case of Trade Audit, the URL [https://tradeaudit.app](https://tradeaudit.app) is hosted from a GitHub Pages
repository.
GitHub Pages can be a simple and free option for static sites. Dynamic data for this site loads from CORS JSON requests
to the main
Trade Audit mobile API server, and the data is highly cacheable. There are many free (or low cost) IaaS solutions
providing
highly configurable caching and DDOS protection such a AWS CloudFront or CloudFlare that will protect your API layer
without a separate Nginx install.




