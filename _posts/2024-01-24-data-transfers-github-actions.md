---
title: "Data Transfers with a Github Action"
categories:
  - Platform
tags:
  - SBT
  - GitHub
excerpt_separator: <!--more-->
---

{% include table-of-contents.html height="100px" %}

Maximizing Github Free Tier as a CI/CD pipeline, using Scala/Java/JVM for almost everything.

    Github for private source repos.
    Github Actions for building.
    Github Packages (Maven) for release backups (500MB limit).
    Local JVM executes your deployment.

Why would you use this?

Other tools like Ansible is probably better for you. Ansible has secure file upload and lots of plugins for simple server-side actions.

If you don't want to create SSH accounts, install clients (other than this one), or want to use Scala instead of custom scripting languages, maybe this is for you.

This project is basically server-side deployment scripts written in Scala, with Akka HTTP receiving builds from Github, so it can easily be integrated as a Route of existing Akka HTTP / Play deployments.
User Permissions

Upload permissions are limited to the ability to publish to Github Packages Maven.

Server-side permissions are completely internal to your server.
Two Deployment Parts

SBT build tasks

    publishAssemblyToGithubPackages: pushes compiled code to Github Packages (Maven)
    uploadAssemblyByPut: pushes compiled code to your server (HTTP PUT)

HTTP Upload Server

    built on Akka, handles HTTP PUT
    validates upload is latest version in Maven, and has correct MD5 checksum
    performs any custom server-side tasks, such as deployment and restarting

{%
include figure image_path="https://raw.githubusercontent.com/stevenrskelton/http-maven-receiver/main/requests.drawio.svg" class="figsvgpadding"
alt="GitHub Actions file push using POST"
caption="GitHub Action to POST artifacts using HTTP uploads to an external receiver"
img_style="padding: 8px;background: white;"
%}



{%
include github_project.html
name="HTTP Maven Receiver"
url="https://github.com/stevenrskelton/http-maven-receiver"
description="See the Complete Code on GitHub"
%}
