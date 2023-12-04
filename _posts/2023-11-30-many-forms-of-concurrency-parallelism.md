---
title: "The Many Different Forms of Concurrency and Parallelism"
categories:
  - Platform
tags:
  - AsyncExecution
  - Serverless
excerpt_separator: <!--more-->
---

Modern software design requires the understanding of the different layers of concurrency and parallelism that can exist.
Abstractions exposed by libraries and frameworks can inadvertently hide layers of parallelism when their focus is the
simplification of others; and libraries trying to treat all levels of parallelism equality can be limited to low level
concepts for their common interface. In order to optimally design and avoid errors, all levels of concurrency and
parallelism need to be understood no matter what framework is chosen.<!--more-->

{% include table-of-contents.html height="400px" %}

# The Basic Idea of Parallelism

Parallelism is a simple concept: multiple computer operations performed at the same time. This requires separate
CPU hardware, such as separate cores in a CPU, separate CPUs, or even separate computers.

> ⓘ There is the concept
> of [SIMD (single instruction, multiple data)](https://en.wikipedia.org/wiki/Single_instruction,_multiple_data)
> parallelism, but these hardware instructions have specific applications and not a general application

Concurrency is a separate but related concept: it is the ability to break up work into separate tasks. Parallelism
requires concurrency, but concurrency doesn't require parallelism. An example of concurrency without parallelism in the
real world is starting 2 books at a time, but being physically unable to read both books at the same time. In the realm
of computers, a stream can have concurrency without parallelism. Elements of the stream can come from multiple sources
however the stream will always have only 1 head element at a time.

## Pitfalls of Parallelism

A simple example of a parallelism pitfall is the increment operator `+=`, _ie_ `i = i + 1`.

Even though `+=` is a simple low-level operator it is not thread safe. When executed in parallel with a standard global
variable `i` the resulting value of `i` is indeterminate. It won't always equal the number of iterations. In a loop
run 30 times, `i` will often equal less than 30.

The explanation is how computers parallelize execution. Each concurrent `+=` operation runs in its own hardware with an
input value `i`. When 2 parallel operations of `+=` start at the same time they both start with the same value of
`i`!  Essentially `i += 1` cannot be parallelized because each execution depends on the previous execution's result.

# Forms of Parallelism

From the perspective of a network service, there can be multiple forms of parallelism. Consider a distributed system
handling HTTP requests in a serverless cloud environment such
as [GCP Cloud Functions](https://cloud.google.com/functions)
or [AWS Lambda](https://docs.aws.amazon.com/lambda/latest/dg/welcome.html).

{%
include figure image_path="/assets/images/2023/11/gcp_cloudfunction_configuration.jpg"
alt="GCP Cloud Function configuration"
caption="GCP Cloud Function **Runtime, build, connections and security settings**"
%}

There are 3 settings in GCP that affect parallelism and concurrency:

- `CPU`
- `Maximum concurrent requests per instance`
- `Minimum number of instances` / `Maximum number of instances`

## CPU

This represents the number of local (virtual) cores available for code execution. This may or may not match the number
of underlying hardware cores, often vCPU are [hyper-threaded](https://en.wikipedia.org/wiki/Hyper-threading) or a
fraction of a hardware core shared with other tenants. Whether a dedicated hardware core or not, the same optimizations
apply with the goal of achieving a desired usage % with minimal OS thread switching.

### Optimal CPU Usage % and Load

There are different optimal CPU usage % for different workloads. A 100% usage will achieve the greatest throughput of
executed instructions, by maintaining a deep queue of work waiting to execute. Any time code execution is queued, the
time in queue is latency introduced into the system. Batch and background workloads are often latency insensitive making
100% CPU usage targets optimal, but software systems benefiting or requiring low latency execution will necessitate
targeting CPU usage percentages < 100%.

GCP [Scaling based on CPU utilization](https://cloud.google.com/compute/docs/autoscaler/scaling-cpu) has a warning
> Caution: If your application takes a long time to initialize on new VMs, Google recommends that you do not set a
> target CPU utilization of 85% or above. In such a case, if your application sees an increase in traffic, your MIG's
> CPUs
> might be at risk of getting overloaded while your application slowly initializes on the new VMs that the autoscaler
> adds.

`CPU Load` is measurement of the contention for the hardware. Optimally the CPU Load will be equal to the number of
cores available, for an 8 core machine a CPU load of 8 will mean each core will typically be executing code without
any code having to wait for an available core. A CPU Load < 8 would mean idle cores, and > 8 would mean tasks waiting
for execution.

#### ExecutionContext and Context Switching

`Context Switching` refers to whenever the executing thread changes on CPU hardware. This process incurs a penalty
for the duration of time required to load new thread CPU instructions and cache from memory. Every OS thread switch
consumes time when the CPU cannot execute code, and this is reported as % of CPU utilization.

Concurrency libraries can offer workarounds utilizing [green threads](https://en.wikipedia.org/wiki/Green_thread) or
with similar concepts of code partitioning that share a single OS thread. In the Java and Scala ecosystem, the
`ExecutionContext` is a mechanism to selecting an `Executor` defining how concurrent code is assigned to threads. Scala
`scala.concurrent.ExecutionContext.Implicits.global` defaults to a `Fork/Join` executor that attempts to balance context
switching by maintaining thread affinity when possible but still allowing work-stealing and reassignment to prevent idle
cores.

It is interesting to note the requirement of an `ExecutionContext` on the Scala `Future` map. This indicates that every
`map` or `flatMap` can perform a context switch under non-ideal conditions. Concurrency libraries such
as [ZIO](https://zio.dev/reference/core/runtime)
and [Twitter Futures](https://twitter.github.io/util/guide/util-cookbook/futures.html) have runtimes build to avoid
an `ExecutionContext` requirement and penalty, and Akka as implemented their own same-thread Executor to work around it
while continuing to use the Scala `Future` construct.

TODO: link to Akka sameThread Executor

## Maximum concurrent requests per instance

As the name implies, this GCP parameter controls the level of concurrency. Without sufficient concurrency parallel
executors can become idle. As mentioned above in [Optimal CPU Usage % and Load](#optimal-cpu-usage--and-load) too much
concurrency will be penalized by context switching overhead.

When using _thread-sharing_ frameworks such Spring WebFlux, this setting can be
arbitrarily high. The framework will fairly allocate CPU resources to requests.

When using _thread-per-request_ frameworks such as Spring MVC, this setting will require tuning as it can't be too low
as to block requests from executing, but setting it too high will allow too many threads to be creating increasing
context-switching and CPU Load.

When using a batch or event-based framework, this will be a critical setting defining how many events can be processed
parallel. Ideally, an event-based system will match the number of concurrent requests to the CPU cores thereby achieving
[Mechanical Sympathy](https://dzone.com/articles/mechanical-sympathy) of matching software and hardware.

## (Minimum / Maximum) number of instances

Often the first 2 GCP parameters, _CPU_ and _Concurrent Requests per Instance_ will depend on the software design used,
and this parameter will depend on the system load. With this in mind, variable load will result in a variable number
of instances. Cloud platforms expose this as [Autoscaling](https://cloud.google.com/compute/docs/autoscaler).

### Distributed Concurrency

The concurrency required to take advantage of multiple instances is different than the currency required to take
advantage of multiple cores.  `Distributed Concurrency` introduces many new concerns that are not present when designing
around `Local Concurrency`.

Distributing data is no longer, _a single copy_ or _always available_.

These concerns are highlighted [Strong versus Eventual Consistency](https://en.wikipedia.org/wiki/Eventual_consistency)
and the [CAP Theorem](https://en.wikipedia.org/wiki/CAP_theorem), respectively.

# Writing Code For All Configurations

- type of work matters, HTTP requests can't be distributed, batch processes and streams can

# Latency and CPU idle

Batch process is latency insensitive, so can ignore

# Libraries and Project Loom

