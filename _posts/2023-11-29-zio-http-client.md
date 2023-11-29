---
title: "ZIO Http Client"
categories:
  - Scala
tags:
  - Akka-Pekko
  - ZIO
---

There are many libraries that allow HTTP requests to be made, it is not usually the crux of a high performance system. 
Even the slowest, blocking library can be made performant with the correct handling in ZIO. With all the options 
available and the low profile, the status of the ZIO HTTP client code in walked through and configured.

{% include table-of-contents.html height="200px" %}

# Alternatives to ZIO HTTP Client

For simplicity in a ZIO system, the obvious choice is to use ZIO-HTTP for client HTTP requests.

https://zio.dev/zio-http

# Client Configuration and Default Settings

These are applicable to `v3.0.0-RC3` (Oct 23, 2023).

## Accept-Encoding Disabled by Default

It is observed that request compression is disabled by default.  It is an understandable default since request 
compression will make requests over a high-speed network slower; especially for typical service communications.
However, requests over the Internet for large request sizes, such as HTML rendered pages will benefit.

To enable decompression (and prefer compressed responses), a configuration change to the `Config` needs to be made,
changing the default `requestDecompression` from `Decompression.No` to either `Decompression.Strict` or `todo`

```scala
lazy val default: Config = Config(
  ssl = None,
  proxy = None,
  connectionPool = ConnectionPoolConfig.Fixed(10),
  maxHeaderSize = 8192,
  requestDecompression = Decompression.No,
  localAddress = None,
  addUserAgentHeader = true,
  webSocketConfig = WebSocketConfig.default,
  idleTimeout = None,
  connectionTimeout = None,
)
```
After update, requests can use `Accept-Encoding` headers for `GZip`, `todo` or `todo`.

## TLS / SSL Certificates 

An SSL Certificate chain needs to be use to validate HTTPS requests, and non-obviously many libraries will use the JVM 
system keystore without configuration. ZIO does not access the system chain by default, it will need to be manually 
configured.  Depending on the system the file can be in multiple locations.  On Mac and Linux a standard location is in
`/etc/ssl/certs`, though for additional security a separate key chain should be used that only includes certificates 
necessary for operation (this can also include self-signed certificates).

ZIO documentation includes an example [https://zio.dev/zio-http/examples/basic/https-client]
ie: 

```scala
val ssl = ClientSSLConfig.FromCertFile("/etc/ssl/certs/ca-certificates.crt")
```

## IPv4 Only

Many cloud deployments use IPv4 exclusively for simplicity and brevity over IPv6.  ZIO HTTP can run into
issues since it has no configurations to disable IPv6.

### JVM Parameters

One can instruct the JVM to prefer IPv4 over IPv6 using JVM parameters on initialization, and also in code.

```shell
-Djava.net.preferIPv4Stack=true
```

```scala
java.lang.System.setProperty("java.net.preferIPv4Stack", "true")
```

### Disabling IPv6 on Linux Network Interfaces

JVM preference may still run into issues if system interfaces are misconfigured with IPv6.
For example, my development laptop has an internal wired NIC `enp5s0` that is unused but still configured with IPv6, 
and this has lead to issues with request failures when the JVM tries to use it.

```
enp5s0: flags=4099<UP,BROADCAST,MULTICAST>  mtu 1500
    ether aa:bb:cc:dd:ee:ff  txqueuelen 1000  (Ethernet)
    RX packets 0  bytes 0 (0.0 B)
    RX errors 0  dropped 0  overruns 0  frame 0
    TX packets 0  bytes 0 (0.0 B)
    TX errors 0  dropped 0 overruns 0  carrier 0  collisions 0

lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536
    inet 127.0.0.1  netmask 255.0.0.0
    loop  txqueuelen 1000  (Local Loopback)
    RX packets 3065851  bytes 409297908 (390.3 MiB)
    RX errors 0  dropped 0  overruns 0  frame 0
    TX packets 3065851  bytes 409297908 (390.3 MiB)
    TX errors 0  dropped 0 overruns 0  carrier 0  collisions 0

wlp0s19f2u5: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500
    inet 192.168.2.52  netmask 255.255.255.0  broadcast 192.168.2.255
    ether 00:11:22:33:44:55  txqueuelen 1000  (Ethernet)
    RX packets 32293  bytes 24960547 (23.8 MiB)
    RX errors 0  dropped 0  overruns 0  frame 0
    TX packets 25432  bytes 4863693 (4.6 MiB)
    TX errors 0  dropped 0 overruns 0  carrier 0  collisions 0
```
The solution is to disable the device, however if it was used there doesn't seem to be an obvious way to 
configure ZIO to not use it.
```shell
ifconfig enp5s0 down
```
Another more drastic change is to completely disable IPv6 in the Linux kernel:
```shell
echo 1 > /proc/sys/net/ipv6/conf/default/disable_ipv6
echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6
```

# Netty Version Conflicts with gRPC-Netty

An issue with `NoClassFoundException` on `SSLPrivateKeyMethod` resulted from Netty version conflicts.

When using `grpc-java` and `zio-http`, both will depend on Netty so it is important to select compatible versions.
There is documentation on [https://github.com/grpc/grpc-java/blob/master/SECURITY.md](gRPC-java security) 
about Netty version compatibility and SSL libraries.  Most users will use `netty-tcnative-boringssl-static` for SSL
implementations.

Complicated since ZIO-HTTP is a client, and already using (ZIO) grpc-java, so this is two separate packages that both
use netty, managed to get working despite having both

| Package        | Version   | Netty Version |
|:---------------|:----------|:-------------:|
| grpc-netty     | 1.53.0    | 4.1.79.Final  |
| zio-http       | 3.0.0-RC2 | 4.1.100.Final |

/project/plugins.sbt

```sbt
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.6")
libraryDependencies += "com.thesamet.scalapb.zio-grpc" %% "zio-grpc-codegen" % "0.6.0"
```

/build.sbt

```sbt
libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % "1.53.0",
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
  "dev.zio" %% "zio-http" % "3.0.0-RC2",
  "io.getquill" %% "quill-jdbc-zio" % "4.7.3",
  "io.netty" % "netty-tcnative-boringssl-static" % "2.0.54.Final" % Runtime classifier "linux-x86_64",
)
```

