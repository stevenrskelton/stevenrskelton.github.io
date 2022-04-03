---
layout: post
title: "Finagle ServerSet Clusters using Zookeeper"
download_sources:
- https://github.com/stevenrskelton/Blog/blob/master/src/test/scala/FinagleServersetClustersUsingZookeeperSpec.scala
---

The key to high availability is redundancy; it follows that if uptime matters, [Finagle](http://twitter.github.io/finagle/) needs to be deployed to multiple servers. This article walks through both the basic multi-host configuration using finagle-core, as well as a more robust deployment scenario utilizing the finagle-serversets module.

With proper architecture, server capacity will scale linearly with the number of servers, for <em>N</em> servers, each should receive <em><sup>1</sup>/<sub>N</sub></em> of all traffic. This is a complex problem that can’t always be handled using an external/hardware load balancer. Any load balancer external to the application is naïve to internal details; configuration changes cannot be appropriately handled, and factors such as connection pooling or variable response times will reduce their overall effectiveness.

Finagle comes with two configurations to handle load balancing internally: a statically defined list of hosts, and a dynamically sized cluster of servers. The first, a static configuration of hosts, mimics that of an external load balancer, where clients are programmed with a list of hosts and Finagle internals will properly balance requests among all servers. The second is a more robust deployment, using finagle-serversets to externally store the server host addresses in [Apache Zookeeper](http://zookeeper.apache.org/). The load balancing is the same, but since the list is external to the clients it can be set, and dynamically changed, throughout operation. The key feature this affords is the ability to scale server capacity to meet variable levels of traffic – taking full advantage of instantaneous cloud deployments.

### Static Server Configuration

Finagle clients can specify multiple hosts during their construction. The `ClientBuilder.hosts` function accepts either a single `SocketAddress`, or a `Seq[SocketAddress]`. When multiple hosts are supplied, Finagle clients can choose which host to connect to, and they are smart about it – weighing factors such as the number of open requests executing on the server, and server responsiveness. These are sufficient to provide basic fail-over and load balancing within a deployment, and work well at creating a highly available deployment without any additional code required on the server.

```scala
val host1: java.net.InetSocketAddress
val host2: java.net.InetSocketAddress
 
ClientBuilder()
  .codec(ThriftClientFramedCodec())
  //static list of hosts
  .hosts(Seq(host1, host2))
  .hostConnectionLimit(Seq(host1,host2))
  .build()
```

### Dynamic Server Configuration

The ServerSets module in Finagle goes one step further, allowing a server cluster to be dynamic. Instead of requiring that server addresses be specified at startup, the location and number of servers can fluctuate dynamically during operation. Operations teams can take full control over the deployment and dynamically scale server capacity to match traffic patterns, providing a better user experience while decreasing costs. Dynamic host addresses also have the benefit of simplifying client-side configuration: even statically sized server clusters with change, whether due to unplanned issues or scheduled maintenance.

Finagle ServerSets require an external server called Apache Zookeeper, a lightweight and usually undemanding network application, to manage the host configurations. For anyone unfamiliar with Zookeeper, there is a good, in depth talk by Patrick Hunt as part of Airbnb’s tech talks available on YouTube:



For the purpose of Finagle, it’s okay to think of Zookeeper as a dynamic configuration store – as long as a server is connected to Zookeeper it is allowed to publicize its address to clients. Finagle clients no longer have to be preprogrammed with a static list of available Finagle hosts, they retrieve and monitor the available servers stored within Zookeeper. Zookeeper is complete with the ability to notify clients when the configuration changes, and Finagle clients will instantly react to added or removed servers.

During client construction, server sets are specified using `cluster` instead of `hosts`.

```scala
val zookeeperHost: java.net.InetSocketAddress
val zookeeperClient = new ZookeeperClient(sessionTimeout, zookeeperHost)
val serverSet = new ServerSetImpl(zk.zookeeperClient, &quot;/testservice&quot;)
val cluster = new ZookeeperServerSetCluster(serverSet)
 
ClientBuilder()
  .codec(ThriftClientFramedCodec())
  //dynamic hosts from zookeeper
  .cluster(cluster)
  .hostConnectionLimit(Seq(host1,host2))
  .build()
```

The server also requires an extra configuration, a few lines of code to connect to Zookeeper.

```scala
val serverHost: java.net.InetSocketAddress
 
val zookeeperHost: java.net.InetSocketAddress
val zookeeperClient = new ZookeeperClient(sessionTimeout, zookeeperHost)
val serverSet = new ServerSetImpl(zk.zookeeperClient, &quot;/testservice&quot;)
val cluster = new ZookeeperServerSetCluster(serverSet)
 
//publicize this server in Zookeeper
cluster.join(serverHost)
```

The use case of Zookeeper seems very simple, and thankfully its installation and configuration is equally so. Most people will find that a simple

```
apt-get install zookeeper zookeeperd
```

is enough to get up and running (on Linux). It’s recommended to run Zookeeper itself in a cluster, however Finagle will continue to work undisrupted should Zookeeper go down; so this isn’t a strict necessity.

Since Zookeeper is less well known despite its ubiquitous use, it’s appropriate to point out that Zookeeper has many uses outside of this limited application. It is the silver bullet to a couple of the more prolific networking problems, so its worth a brief inspection. Unless control over [ACL security](http://en.wikipedia.org/wiki/Access_control_list) is required, Zookeeper’s API is only 7 methods, so it is quickly usable following the [documentation on their wiki](https://cwiki.apache.org/confluence/display/ZOOKEEPER/Index).

While the native Zookeeper client is exposed by the serversets implementation, expanded use cases of Zookeeper outside of Finagle will often benefit from the use of [Apache Curator](http://curator.incubator.apache.org/), which comes complete with precooked recipes for common tasks, as well as a wrapper client meant to alleviate the boiler-plate coding necessary for issue free operation. However, for the purposes of Finagle’s use case, or simple CRUD operations for that matter, the standard Zookeeper client is quite sufficient.

The internals to Finagle’s use of Zookeeper are simplistic, the `cluster.join` method takes a `SocketAddress` and creates an ephemeral node on the Zookeeper server containing JSON:

```json
{"serviceEndpoint":{"host":"myserver","port":10000},"additionalEndpoints":{},"status":"ALIVE"}
```

The JSON structure stores only the basic description a server host address – there is nothing specific to Finagle. This opens the possibility to reuse of Finagle’s ServerSet within any other service which would benefit from network discovery.

A standard ZookeeperClient could also be used to loop through all service endpoints:

```scala
import com.twitter.common.zookeeper.{ ServerSets, ServerSetImpl }
import com.twitter.thrift.ServicInstance
 
val zkClient: com.twitter.common.zookeeper.ZookeeperClient
 
//standard zookeeper client, unwrapped from Twitter
val zk: org.apache.zookeeper.Zookeeper = zkClient.get
 
val jsonCodec = ServerSetImpl.createJsonCodec
val serverInstances: Seq[ServiceInstance] = for
 (zNode <- zk.getChildren("/testservice", false)) yield {
 
  val serverData = zk.getData(s"/testservice/$zNode", false, null)
  //serverData is Array[Byte] of JSON
  //val json = new String(serverData, "UTF-8")
  ServerSets.deserializeServiceInstance(serverData, jsonCodec)
}
```

While the above code used Twitter `ServerSets` for JSON deserialization, any standard library could have been used, removing all dependency on Twitter/Finagle libraries.

Integration tests can be run without an external Zookeeper server, Curator comes with an in process TestServer, or it’s possible to manually import the code from [Twitter ZkInstance](https://github.com/twitter/finagle/blob/master/finagle-serversets/src/test/scala/com/twitter/finagle/zookeeper/ZkInstance.scala) into your project.

