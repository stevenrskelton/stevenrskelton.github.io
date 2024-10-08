---
permalink: /about/
title: "About"
---
<div class="img-bg" style="
  background-image:url('/assets/images/about/bg.jpg');
  max-width: 1040px;">

  <h1 style="margin-bottom:0">Principal Software Engineer</h1>
  <p>
    <strong> 🇨🇦&nbsp;Toronto, Canada</strong>
  </p>

  <p>
    <strong>Honours Bachelor of Mathematics</strong><br>
    Double Major Honours (HBMath) in <i>Pure Mathematics</i> and <i>Applied Mathematics</i> from the University of Waterloo
  </p>

  <h2 id="work-summary">Work Summary</h2>

  <p>
    <strong>8 years e-commerce</strong> consultant for <em>Walmart Canada</em> and <em>Walmart Global Tech</em>, leading technical development of the e-commerce website 
    and API for mobile clients, with key successes in cloud re-platforming, continuous delivery and integrations of internal and 3rd-party microservices.
  </p>

  <p>
    <strong>7 years financial services and fintech</strong> consulting, including:
  </p> 
  <ul style="padding-bottom:0.5em">
    <li><em>Gluskin Sheff + Associates</em></li>
    <li><em>Dundee Wealth Management</em></li>
    <li><em>Home Trust Company</em></li>
    <li><em>Point72 Asset Management</em> (formally S.A.C. Capital Advisors)</li>
    <li><em>Corient Private Wealth</em> (part of CI Financial)</li>
    <li><em>PureFacts Financial Solutions</em></li>
  </ul>

  <p>
    <strong>3 years</strong> consulting and FTE across a diverse range of e-commerce and customer-focused product development.
  </p>
</div>

## Principal Skills

- **Leader**: technology evangelist, ways of working, skill development, mentoring, and inter-team cohesion.
- **Proactive**: architectural runway, enterprise best practices, industry awareness.
- **Cloud**: SRE, DevOps, scaling, resiliency, security, disaster recover and respecting the 95th percentile.
- **R&D**: benefit analysis, feasibility, integration strategies, and working with key stakeholders on adoption timelines.
- **Data**: PII governance, read-write asymmetry, event-driven, low-latency streams.

## Enterprise Certifications

<div>
{%
include certification.html
name="Google Cloud<br>Associate Cloud Engineer"
description="Google Cloud 0e4c4f24bc7945a499aaad77a0530eaf Series 144815 Jul 2024"
img="/assets/images/about/certifications/gcp_associate_cloud_engineer.png"
url="/assets/images/about/certifications/AssociateCloudEngineer20240711-7-h9f128.pdf"
%}
{%
include certification.html
name="SAFe 5.1<br>Advanced Scrum Master"
description="Scaled Agile, Inc. 89485707-5065 Apr 2024"
img="/assets/images/about/certifications/cert_mark_SASM_badge_large_300px.png"
url="/assets/images/about/certifications/safe-certified-advanced-scrum-master.pdf"
%}
{%
include certification.html
name="SAFe 5.1<br>Scrum Master"
description="Scaled Agile, Inc. 49059875-5668 Jun 2023"
img="/assets/images/about/certifications/cert_mark_SSM_badge_large_300px.png"
url="/assets/images/about/certifications/safe-certified-scrum-master.pdf"
%}
{%
include certification.html
name="SAFe 5.1<br>Leading SAFe"
description="Scaled Agile, Inc. 49562821-2030 Jun 2022"
img="/assets/images/about/certifications/cert_mark_SA_badge_large_300px.png"
url="/assets/images/about/certifications/safe-leading-safe.pdf"
%}
</div>

## Public Portfolio

<div class="img-bg" style="
  background-image: url('/assets/images/about/dancing_robots.jpg');
  margin-top: 50px;
  margin-bottom: 30px;
  max-width: 1040px;
">
  <h1>Trade Audit Android App</h1>
  
  <style>
    @media(min-width: 400px) {
      .img-fill {
        float: left;
        margin-right: 20px;
        margin-bottom: 4px;
      }
    }
  </style>
  <div class="img-fill" style="
    text-align: center;
    margin-bottom: 1em;
    font-size: small;
  ">
    <a href="https://tradeaudit.app" target="_blank" style="text-decoration: none;">
     <img src="/assets/images/about/tradeauditapp.png" title="Trade Audit Mobile App" 
      style="
        width: 160px;
        box-shadow: none;
        padding: 14px 20px 4px 20px;
      " alt="Trade Audit"/><br/>
     <span style="font-weight:bold;color:#1da1f2;">https://tradeaudit.app</span>
    </a><br>
    <a href="https://play.google.com/store/apps/details?id=app.tradeaudit" target="_blank"><img alt="Get it on Google Play" style="height:60px;box-shadow:none;" height="60" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"></a>
  </div>
  <p>
    Stock market speculation is at a record high. Individuals are following social media accounts trying
    to navigate meme-stocks, HODLs, and the opacity of SPACs. There is no accountability, no oversight, and 
    no rules to what can be presented as knowledgeable advice on social media.
  </p>
  <p>
    Trade Audit is a mobile application that creates accountability.
  </p>
  <p>
    Use Trade Audit to catalogue any stock advice found on the internet.  Stock trades and ideas can be tracked 
    creating an immutable track record that the social media accounts refuse to provide.
  </p>
  <div style="height:0;clear:left"></div>
</div>

### GitHub Repositories

<div style="
  display: flex;
  flex-wrap: wrap;
  -webkit-flex-wrap: wrap;
  list-style: none;
  padding-inline-start: 0px;
  padding-top: 0.75em;
">

{% assign list = site.github.public_repositories | where: "archived", false | where: "fork", false | sort: 'pushed_at' | reverse %}
{% for repository in list %}
  {% include github_repository.html repository_id=repository.id %}
{% endfor %}

</div>

### Archived GitHub Repositories
Polymer Web Components and older incompatible JavaScript and Scala code.
*Polymer Library was deprecated in 2019 when functionality became browser native, <wbr>remaining Polymer functionality became [Lit](https://lit.dev/)*

<div style="display:flex;flex-wrap:wrap;-webkit-flex-wrap:wrap;list-style:none;padding-inline-start:0px;">

{% assign list = site.github.public_repositories | where: "archived", true | where: "fork", false | sort: 'stargazers_count' | reverse %}
{% for repository in list %}
{% if repository.stargazers_count > 2 %}
  {% include github_repository.html repository_id=repository.id %}
{% endif %}
{% endfor %}
  
</div>
