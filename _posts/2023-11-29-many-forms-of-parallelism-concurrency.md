---
title: "The Many Different Forms of Parallelism and Concurrency"
categories:
  - Platform
tags:
  - AsyncExecution
  - Serverless
excerpt_separator: <!--more-->
---
There are different concepts of parallelism and concurrency that exist and need to be considered when architecting 
software applications. The abstractions offered by libraries and frameworks to hide parallelism can be convenient 
ways to simplify code, but without understanding each parallelism concept it is easy to introduce errors or fail to 
optimally perform in distributed environments.

{% include table-of-contents.html height="200px" %}

# The Basic Idea of Parallelism

A lucid example of parallelism is the increment operator `+=`; ie `i = i + 1`.  Running this operation in a parallelized
loop on a global `i` will lead to errors.  If it is run 30 times, at the end `i` won't always equal 30.  Sometimes it 
will be less than 30.

No matter the mechanism to run work in parallel, the library might call them virtual threads, green threads, tasks, 
events, or something completely different, if it was run in parallel it had to have run on separate cores of the
CPU.  The issue isn't in the parallelism, it is in the memory reference.

Computer operations aren't instantaneous, and there will necessarily be a delay between when `i` is read from memory and
when the `i + 1` result is calculated and stored. During that delay any changes to the memory storing `i` will be lost
when the `i + 1` result is stored back to `i`.

## Parallelism and Concurrency Defined

Parallelism only exists when there are multiple cores, either locally on the same machine or on separate machines connected over 
a network, available to execute code.  Concurrency is simply the ability to define separate blocks of executable code.

# Forms of Parallelism

From the perspective of a network service, there can be multiple forms of parallelism. Let's consider code that handles
requests to serve an HTML page in a serverless cloud environment, such as using [GCP Cloud Functions](https://cloud.google.com/functions) or [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html).

{%
    include figure image_path="/assets/images/2023/11/gcp_cloudfunction_configuration.png"
    alt="GCP Cloud Function configuration"
    caption="GCP Cloud Function **Runtime, build, connections and security settings**"
%}

There are 3 settings in GCP that affect parallelism and concurrency: 
- `CPU`
- `Maximum concurrent requests per instance`
- `Minimum number of instances` / `Maximum number of instances`

## CPU

## Maximum concurrent requests per instance

## (Minimum / Maximum) number of instances

# Writing Code For All Configurations

- type of work matters, HTTP requests can't be distributed, batch processes and streams can

# Latency and CPU idle

GCP [Scaling based on CPU utilization](https://cloud.google.com/compute/docs/autoscaler/scaling-cpu) has a warning
> Caution: If your application takes a long time to initialize on new VMs, Google recommends that you do not set a target CPU utilization of 85% or above. In such a case, if your application sees an increase in traffic, your MIG's CPUs might be at risk of getting overloaded while your application slowly initializes on the new VMs that the autoscaler adds.

Batch process is latency insensitive, so can ignore

# Libraries and Project Loom

