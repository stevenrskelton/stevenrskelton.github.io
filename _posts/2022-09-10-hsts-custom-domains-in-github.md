---
title: "HTTP Strict Transport Security (HSTS) Domains hosted on Github Pages"
categories:
  - Platform
tags:
  - Github
---
Github Pages is free hosting for static content webpages, and a cost effective way to publish micro-sites for all of your projects.  Can it handle custom domains, and HSTS domains?  How about multiple custom HSTS domains?

# HSTS Domains force HTTPS
Every internet connection should be over HTTPS. The [Let's Encrypt](https://letsencrypt.org/) project offers free TLS certificates so there is no excuse to use HTTP, and **many** privacy reasons not to. Every one of your favourite ecommerce websites already force HTTPS, however the most common implementation is to return a *301 Moved Permanently* response to any HTTP request directing to the HTTPS url. 

But there is a better, formal way to do this called [HTTP Strict Transport Security (HSTS)](https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security).
HSTS is an official HTTP mechanism to enforce secure HTTPS connections at the (sub)domain level without 301s.

# HTTPS-Only Domains: .dev .app .page

There are many Top Level Domains (TLDs) now, and adoption is growing.  The original TLDs are archaic and unhelpful - but simple.  Thinking in .com terms is also very US centric, the rest of the world has already moved to country code TLDs like .cn (China) or .de (Germany) to offer better localized (and language) representation.

Outside of regional TLDs, .com is meant to represent a commercial use.  Is this the best way to present your site?  Tech startups begain to (mis)use the `.io` TLD to separate themselves, along with avoiding paying for domain squating. 

Out of the +1500 TLDs available, there are 3 common ones: `.dev`, `.app`, and `.page` that make HTTPS mandatory using HSTS.  That means you cannot serve *any* traffic over HTTP. These are promoted on Google Domains' security page https://domains.google/tld/security/ and are likely the precurser to all domains using HSTS.

# Github Pages on HTTPS-Only Domains

Short answer, **YES you can use HTTPS-Only domains on Github Pages.**

The only caveat is that the pages won't be served until the `Enforce HTTPS` checkbox has been successfully enabled. 

