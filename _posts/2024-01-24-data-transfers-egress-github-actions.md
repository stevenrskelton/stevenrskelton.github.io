---
title: "Data Transfers and Egress within a GitHub Action"
categories:
  - DevOps/Platform
tags:
  - SBT
  - GitHub
excerpt_separator: <!--more-->
examples:
  - http-maven-receiver
---

The free tier of GitHub Packages has limited bandwidth to download private artifacts; which can make it unsuitable for
use in a CI/CD pipeline for projects on a budget. In an effort to increase GitHub Packages' usability, this article
develops an alternative approach minimizing the dependency on GitHub Packages as hot storage, but preserving it as a
viable cold storage, durable storage solution.<!--more--> Building out a cost-effective CI/CD pipeline on the GitHub
platform means utilizing the unlimited egress bandwidth afforded to GitHub Actions to its fullest potential.

{% include table-of-contents.html height="100px" %}

# GitHub Packages as a Maven repository

In an earlier article, [Downloading from GitHub Packages using HTTP and Maven]({% post_url
2022-05-downloading-from-github-packages-using-http-and-maven %}) we investigated GitHub Packages as a Maven
repository for Java artifacts. Standard practices with any network service is evaluating the benefit of implementing a local cache. A
local cache can speed up downloads, allow customized permissioning, and increased resiliency against network failures.
Even if a local Maven repository proxy cache such as Artifactory or Nexus is selected, the question remains of how to
get artifacts into the local cache if GitHub Packages transfer limits are being hit.

# GitHub Actions have Unlimited Egress (Transfer-Out)

In a GitHub CI/CD pipeline, where compilation occurs within a GitHub Action, a solution is to utilize the unlettered
egress bandwidth available during post-compilation actions. Compiled artifacts can be stored both in GitHub Packages and
transferred to a Nexus or Artifactory proxy cache when bandwidth is available for free, avoiding metered egress being
made from the proxy to GitHub Packages.

## Security and Implementation of a push solution

### Maven Repository Checksums

The question is now of how to securely transfer files out of a GitHub Actions to remote endpoints. All receivers should
accept only authenticated requests from GitHub Actions. Does this require receivers to implement token authentication?
Practically speaking no, taking a step back this isn't about user authentication, it is about file authentication. For a
file to be authentic it needs to exist within GitHub Packages. If the receiver is uploaded a file, that file is
authentic and secure if and only if it has a corresponding Maven checksum in our GitHub Packages repository.

### Authentication Flow

An authentication mechanism beyond GitHub is unwanted and unnecessary. HTTP requests to any external receiver can be
authenticated by verifying the request includes a valid GitHub auth token. The files the requests are uploading can be
validated by accessing GitHub Packages using the GitHub auth token and comparing checksums.

{%
include figure
image_path="https://raw.githubusercontent.com/stevenrskelton/http-maven-receiver/main/requests.drawio.svg"
class="figsvgpadding"
alt="GitHub Actions file push using POST"
caption="GitHub Action to POST artifacts using HTTP uploads to an external receiver"
%}

# HTTP Server for receiving and validating GitHub Action upload requests

This project is basically server-side deployment scripts written in Scala, with Akka HTTP receiving builds from GitHub,
so it can easily be integrated as a Route of existing Akka HTTP / Play deployments.
User Permissions

Upload permissions are limited to the ability to publish to GitHub Packages Maven.

Server-side permissions are completely internal to your server.
Two Deployment Parts

SBT build tasks

    publishAssemblyToGitHubPackages: pushes compiled code to GitHub Packages (Maven)
    uploadAssemblyByPut: pushes compiled code to your server (HTTP PUT)

HTTP Upload Server

    built on Akka, handles HTTP PUT
    validates upload is latest version in Maven, and has correct MD5 checksum
    performs any custom server-side tasks, such as deployment and restarting

