---
title: "SLF4J Application Logging to Slack"
categories:
  - DevOps/Platform
  - Scala
tags:
  - Slack
published: false
---

Slack is positioning itself as your work ecosystem. And they want ALL interactions in their platform. Their app store
and Block UI is a siren song to pay Salesforce (owner of Slack) to manage your workflow indefinitely. For notifications
Slack seems pretty good, better than Microsoft Teams the only generalized competitor left since Atlassian HipChat which
was discontinued in 2019 after being bought by Slack.

There are Slack apps for most popular frameworks and CI pipelines, and official Slack libraries in all popular languages
to create your own app when one doesn't exist or doesn't quite fit your workflow.

## Logging to Slack

Here we will create our own custom app, to offload our logging into Slack because:

- Slack is popular and we already use it
- Slack API is well supported
- Not everything needs Splunk level logging queries
- **Slack has a free tier**

The Slack free tier allows access to the 10K of the most recent messages, which is sufficient for our small project
needs.
A few considerations:

- Slack [rate limts](https://api.slack.com/docs/rate-limits) are per method, per workspace.
- one-word messages count the same as 500 word multiple paragraph messages
- open source projects might already exist

For a Scala project, it makes sense to use SLF4J which is a simple facade library supporting multiple underlying logging
frameworks:

- Log4j
- Logback
- Jakarta Commons
- Simple Logging
- java.util.logging

It is important to note that Slack is trying to phase our their Incoming Web Hooks interface in favour of their App API.

| GitHub                                                                      | Stars | Last Update | Interface | Method   |
|-----------------------------------------------------------------------------|------:|:-----------:|-----------|----------|
| [stagnationlab/slack-logger](https://github.com/stagnationlab/slack-logger) |    14 |  Sep 2021   | NPM       | API      |
| [iotgdev/slack_logging](https://github.com/iotgdev/slack_logging)           |     2 |  May 2020   | Python    | Web Hook |
| [gmethvin/logslack](https://github.com/gmethvin/logslack)                   |     1 |  Jul 2019   | Logger    | API      |
| [tobias-/slack-appender](https://github.com/tobias-/slack-appender)         |     7 |  Oct 2018   | Log4j     | Web Hook |
| [jvz/log4j-slack](https://github.com/jvz/log4j-slack)                       |     1 |  May 2017   | Log4j     | Web Hook |
| [rage28/log4j-slack](https://github.com/rage28/log4j2-slack)                |     3 |  Aug 2017   | Log4j     | Web Hook |
| [TheConnMan/SlackLogger](https://github.com/TheConnMan/SlackLogger)         |     4 |  Jan 2016   | Log4j     | Web Hook |

It looks like there are active projects for NPM and Python, but only 1 suitable Logger for Scala written by the former
maintain for the [Play Framework](https://www.playframework.com/) for Scala. It looks great for simple uses, but let's
create our own so we can apply it to a more advanced use case.

## To Continue
