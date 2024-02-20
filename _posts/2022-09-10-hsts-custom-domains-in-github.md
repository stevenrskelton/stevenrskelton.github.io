---
title: "HTTP Strict Transport Security (HSTS) Domains on GitHub Pages"
categories:
  - Platform
tags:
  - GitHub
---

GitHub Pages is free hosting for static content webpages, and a cost-effective way to publish microsites for all of
your projects. Can it handle custom domains, and HSTS domains? How about multiple custom HSTS domains?

{% include table-of-contents.html height="300px" %}

# HSTS domains force HTTPS

Every internet connection should be over HTTPS. The [Let's Encrypt](https://letsencrypt.org/) project offers free TLS
certificates so there is no excuse to use HTTP, and privacy reasons not to. Every one of your favourite
ecommerce websites already force HTTPS, however the most common implementation is to return a *301 Moved Permanently*
response to any HTTP request directing to the HTTPS url.

But there is a better, formal way to do this
called [HTTP Strict Transport Security (HSTS)](https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security).
HSTS is an official HTTP mechanism to enforce secure HTTPS connections at the (sub)domain level without 301s.

# HTTPS-Only Domains: .dev .app .page

There are many Top Level Domains (TLDs) now, and adoption is growing. The original TLDs are archaic and unhelpful - but
simple. Thinking in .com terms is also very US centric, the rest of the world has already moved to country code TLDs
like .cn (China) or .de (Germany) to offer better localized (and language) representation.

Beyond regional TLDs, .com is meant to represent a commercial use. Is this the best way to present your site? Tech
startups began to (mis)use the `.io` TLD to separate themselves, along with avoiding paying for domain squaring.

Out of the +1500 TLDs available, there are 3 common ones: `.dev`, `.app`, and `.page` that make HTTPS mandatory using
HSTS. That means you cannot serve *any* traffic over HTTP. These are promoted on Google Domains' security
page [https://domains.google/tld/security/](https://domains.google/tld/security/) and are likely the precursor to all
domains using HSTS.

# GitHub Pages on HTTPS-Only Domains

Short answer, yes you can use HTTPS-Only domains on GitHub Pages.

The only caveat is that the pages won't be served until the `Enforce HTTPS` checkbox has been successfully enabled.

## DNS Setup

GitHub pages has [good documentation](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/about-custom-domains-and-github-pages)
on this, basically to point any subdomain, ie: www.example.com to your GitHub Pages you will need to create a `CNAME`
entry at your DNS provider. The `CNAME` is an entry in the DNS record that points your `www.example.com`
to `<user>.github.io`. It is the DNS equivalent of an HTTP redirect, it is telling browsers requesting `www.example.com`
to use `<user>.github.io` instead.

### GitHub Pages Apex Domain

Apex domains are what you purchase when you "buy a domain". You choose a TLD (Top Level Domain) such as `.com` or `.eu`,
and then a unique apex domain in it, such as `example.com`. While you may run your website on `www.example.com`
and `api.example.com`, it is import to also set up the apex. The only difference is a `CNAME` doesn't work for _apex_
domains, These need a more low level approach; instead of setting up `CNAME` to point to another domain you need to use
an `A` record set to the GitHub server IP addresses:

No big deal, at your DNS provider, add an `A` record of:

```
185.199.108.153
185.199.109.153
185.199.110.153
185.199.111.153
```

and an `AAAA` record (support IPv6) of:

````
2606:50c0:8000::153
2606:50c0:8001::153
2606:50c0:8002::153
2606:50c0:8003::153
````

The next step is easy to forget, but _very_ important.

### Verify Your Domain

If you don't verify, ie: prove ownership of your domain, then there are cases where other users can pretend that they
own it and use it for their pages. Should your GitHub Pages every get disabled the next person to request _your domain_
for their GitHub Page will get it! This is caused by the limitation that DNS isn't part of HTTP, it is completely
separate.

When your `CNAME` or `A` record points to `<user>.github.io` it is a DNS configuration to resolve your `example.com`
domain to whatever IP is used by `<user>.github.io`, which are the GitHub Pages server IPs. It then sends an HTTP
request for `example.com` to one of those GitHub Pages IPs.

If their GitHub Pages is set to use your domain as its custom domain their site will be used. Thankfully, stopping this
from happening is easy
and [explained in the documentation](https://docs.github.com/en/pages/configuring-a-custom-domain-for-your-github-pages-site/verifying-your-custom-domain-for-github-pages).
The solution adding a `TXT` record in your DNS record with a specific token value that proves you own the domain. This
is a common approach used by AWS, Google Adsense, etc.

### Don't Use Wildcard Subdomains

Very similar to how domain hijacking happens, is the exploitation of `CNAME` wildcards directing `*.example.com` to
GitHub Pages. The problem here is that your GitHub Pages site can only have 1 domain (plus the apex domain) and you are
pointing an unlimited number of subdomains to GitHub Pages. Let's say you set up your site to be `www.example.com` (
and `example.com`) but wildcard your `CNAME`, this means requests to `blog.example.com` also are directed to GitHub
Pages. The first GitHub Pages site to set their custom domain to `blog.example.com` will be served!  So don't
use `CNAME` wildcards.

# Conclusion

The flexibility of `CNAME` records will create security exploits when misconfigured, but also allows for many use-cases.
A common example would be to have a `<user>.github.io` site on `www.example.com`, plus individual repo sites
on `www.example.com/repo1`, `www.example.com/repo2`, etc., plus other domains such as `repo3.com` pointing to your other
repo pages. It's all done by using the GitHub Pages UI and setting all `CNAME` to `<user>.github.io`
