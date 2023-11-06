---
published: false
title: "ZIO Http (Client) Preview"
categories:
  - Scala
tags:
  - Akka-Pekko
  - ZIO
---

https://zio.dev/zio-http

Enable `Accept-Encoding`, compression not enabled by default.
```
  private val defaultClientConfig = ZClient.Config.default
  private val sslConfig = ClientSSLConfig.FromCertFile("/etc/ssl/certs/ca-certificates.crt")
```

```
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


Add system SSL certs:
Expanding on the example https://zio.dev/zio-http/examples/basic/https-client

```
private val sslConfig = ClientSSLConfig.FromCertFile("/etc/ssl/certs/ca-certificates.crt")
```


Part about disabling IPv4, 

java.lang.System.setProperty("java.net.preferIPv4Stack", "true")
-Djava.net.preferIPv4Stack=true
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

```
ifconfig enp5s0 down
```
Disable in the kernel
```
echo 1 > /proc/sys/net/ipv6/conf/default/disable_ipv6
echo 1 > /proc/sys/net/ipv6/conf/all/disable_ipv6
```



NoClassFoundException SSLPrivateKeyMethod
Make sure Netty and Netty's SSL binaries are compatible versions as per netty-tcnative-boringssl-static

Complicated since ZIO-HTTP is a client, and already using (ZIO) grpc-java, so this is two separate packages that both use netty, managed to get working despite having both 

netty-4.1.86 and netty-4.1.93 installed
