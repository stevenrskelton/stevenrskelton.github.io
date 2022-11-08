---
title: "HTTP Strict Transport Security (HSTS) Domains on Github Pages"
categories:
  - Platform
tags:
  - Github
---
Github Pages is free hosting for static content webpages, and a cost effective way to publish micro-sites for all of your projects.  Can it handle custom domains, and HSTS domains?  How about multiple custom HSTS domains?

## HSTS domains force HTTPS
Every internet connection should be over HTTPS. The [Let's Encrypt](https://letsencrypt.org/) project offers free TLS certificates so there is no excuse to use HTTP, and **many** privacy reasons not to. Every one of your favourite ecommerce websites already force HTTPS, however the most common implementation is to return a *301 Moved Permanently* response to any HTTP request directing to the HTTPS url. 

But there is a better, formal way to do this called [HTTP Strict Transport Security (HSTS)](https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security).
HSTS is an official HTTP mechanism to enforce secure HTTPS connections at the (sub)domain level without 301s.

## HTTPS-Only Domains: .dev .app .page

There are many Top Level Domains (TLDs) now, and adoption is growing.  The original TLDs are archaic and unhelpful - but simple.  Thinking in .com terms is also very US centric, the rest of the world has already moved to country code TLDs like .cn (China) or .de (Germany) to offer better localized (and language) representation.

Outside of regional TLDs, .com is meant to represent a commercial use.  Is this the best way to present your site?  Tech startups begain to (mis)use the `.io` TLD to separate themselves, along with avoiding paying for domain squating. 

Out of the +1500 TLDs available, there are 3 common ones: `.dev`, `.app`, and `.page` that make HTTPS mandatory using HSTS.  That means you cannot serve *any* traffic over HTTP. These are promoted on Google Domains' security page [https://domains.google/tld/security/](https://domains.google/tld/security/) and are likely the precurser to all domains using HSTS.

## Github Pages on HTTPS-Only Domains

Short answer, **YES you can use HTTPS-Only domains on Github Pages.**

The only caveat is that the pages won't be served until the `Enforce HTTPS` checkbox has been successfully enabled. 

### How Do I Set It Up?

Github pages has good documentation on this, basically you will need to create a `CNAME` entry at your DNS provider to point to your `www.example.com` to `<user>.github.io`.  This is the DNS equivalent of an HTTP redirect, it is telling browsers requesting `www.example.com` to use `<user>.github.io` instead.

The only hard thing is a `CNAME` doesn't work for _apex_ domains, that is `example.com` without the `www`. These need a more low level approach,  instead of setting up `CNAME` to point to another domain you need to use an `A` record pointing to the Github server IP addresses:

No big deal, at your DNS provider, add an `A` record for
```
185.199.108.153
185.199.109.153
185.199.110.153
185.199.111.153
```
and an `AAAA` (support IPv6) for
````
2606:50c0:8000::153
2606:50c0:8001::153
2606:50c0:8002::153
2606:50c0:8003::153
````

The next step is easy to forget, but _very_ important. 

[https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/verifying-your-custom-domain-for-github-pages](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/verifying-your-custom-domain-for-github-pages)

If you don't do this, then should your Github Pages every get disabled someone else can host their Github Pages on your domain! The limitation of using DNS is that it isn't part of HTTP.  When your `CNAME` or `A` record points to `<user>.github.io` it is used to resolve the IP, and then browsers make an HTTP request to that IP for your `example.com`.  Github webservers have no idea if the browser wanted `<user>.github.io` or `<malicious-user>.github.io`.  So should your Github Pages disappear, _malicious-user_ could easily set their Github Pages to the `example.com` custom domain and their site would be served instead of yours!

The solution is in the link above, the `TXT` record stores a value unique to `<user>.github.io`, so Github Pages knows that it should only ever serve your site.

Very similiar to this is if you use a `CNAME` wildcard, as in pointing `*.example.com` to Github Pages.  The problem here is that your Github Pages site can only have 1 domain (plus the apex domain).  Let's say you set up your site to be `www.example.com` (and `example.com`).  Then your `CNAME` also points every subdomain, like `blog.example.com` to Github Pages.  Any user can claim them for their Github Pages custom domain - so *don't do this*.

## Conclusion

The flexibility of `CNAME` records will create security exploits when misconfigured, but also allows for many use-cases.  A common example would be to have a `<user>.github.io` site on `www.example.com`, plus individual repo sites on `www.example.com/repo1`, `www.example.com/repo2`, etc, plus other domains such as `repo3.com` pointing to your other repo pages.   It's all done by using the Github Pages UI and setting all `CNAME` to `<user>.github.io`
