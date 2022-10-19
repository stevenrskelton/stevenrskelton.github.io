---
title: "TradeAudit Mobile App Infrastructure"
categories:
  - Platform
  - Scala
  - Dart
tags:
  - TradeAudit
  - AWS
  - Github
---
The release of the [TradeAudit](https://tradeaudit.app) mobile app is almost here. It is currently in MVP stage, but its infrastructure is a pretty typical cloud based deployment.  This article discusses design choices made, evaluating how effective they were.<!--more-->

### Mobile App Requirements

TradeAudit is a mobile app targeting anyone using Twitter for stock trading ideas/recommendations. 
This side of Twitter is colloquially referred to as "fintwit", ie: _Financial Twitter_.

Twitter has a feature called "cashtags" where users can represent stock ticker symbols within Tweets using a dollar sign, for example `$spy` represents the `SPDR S&P 500 ETF`.
The mobile app allows Tweets made about stocks (with or without cashtags) to be recorded, tracked via price information, and aggregated to create historical performance records for Twitter accounts.

High level, this requires:
- [Mobile App iOS/Android Client](#mobile-app-iosandroid-client), obviously.
- [Server-side API](#server-side-api) with both relational data representing trades made by Twitter accounts, and random-access stock pricing data.
- [Support Website](#support-website) which can provide additional information about the app outside of the Apple App Store and Google Play Store pages.

![Infrastructure](/assets/images/2022/10/tradeaudit-release-infrastructure.png)

## Mobile App iOS/Android Client

Flutter, supports both iOS and Android.  [gRPC](https://grpc.io/)

## Server-side API

### API hosting for gRPC and support website.
Akka HTTP (in Scala)

### Databases

AWS DynamoDB
AWS SQS
PostgreSQL

## Support Website

Github pages, host privacy policy and page for URL.
CloudFront.



