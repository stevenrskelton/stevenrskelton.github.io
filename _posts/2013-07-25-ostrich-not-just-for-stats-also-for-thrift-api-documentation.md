---
#layout: post
title: "Ostrich. Not just for stats, also for documentation."
categories:
  - Thrift
  - Scala
---

Ostrich is a stats collector and reporter created by Twitter, and it is a welcome addition to any [Finagle](http://twitter.github.io/finagle/) ([Apache Thrift](http://thrift.apache.org/)) implementation.  At its core it uses an extremely lightweight `com.sun.net.httpserver.HttpServer` to handle JSON and HTML requests.

Consider using the built in Ostrich stats server to host your Thrift API documentation.

One easy way to publish your API is to publish you Thrift files. Under the JVM, it’s as easy as moving them to the `/resources` folder. Also included in the standard Thrift code generator is the ability to generate html – a complete, user-friendly and navigable website which includes all of your javadocs properly formatted.

```
./thrift --gen html:standalone
```

The best reason to include your API documentation within your jar is your documentation will always be in sync with your deployed code.

First, a little java inspired code to list all entries in the jar.

```scala
lazy val thriftJarEntries: Seq[String] = {
 
  import java.util.jar.JarFile
  import java.io.File
  import java.net.URI
 
  val classInThriftJar = OstrichService.getClass
  val classLoader = classInThriftJar.getClassLoader
  val threadContextClass = classInThriftJar.getName.replace('.', '/')
  val url = classLoader.getResource(threadContextClass + ".class")
  val path = url.getPath
  val jarExt = ".jar"
  val jarPath = path.substring(0, path.indexOf(jarExt)+jarExt.length)
  import java.util.
  val jarFile = new JarFile(new File(new URI(jarPath)))
 
  import scala.collection.JavaConversions._
  jarFile.entries.map(_.getName).toSeq
}
```

There is no easy way to use a wildcard search for jar resources, so it’s necessary to load the entire list and filter it manually.
Once we have all the names of our thift files, we need code to serve up their contents for use in a browser response.

```scala
def getJarResourceAsText(name: String): String = {
  val in = getClass.getResourceAsStream(name)
  io.Source.fromInputstream(in).getLines.mkString("\n")
}
```

With all the heavy lifting out of the way, we are left to wire up routes so our HttpServer can respond to file requests. By default the Ostrich service will respond to all browser requests with the `text/html` content type, which is perfect for any HTML documentation.

```scala
val ostrichService = new AdminHttpService(port, backlog, statsCollection, runtime)
 
thriftJarEntries.filter(_.endsWith(".html")).foreach(entry => {
  ostrichService.addContext("/thrift/" + entry){
    () => getJarReferenceAsText("/" + entry))
  }
})
```

This takes care of our HTML generated docs, be when we would like our thrift files to be treated as plain text. This is accomplished by implementing a new `CustomHttpHandler` for our `HttpServer`, specifying a plain text content type.

```scala
class PlainTextHandler(content: String) extends CustomHttpHandler {
  def handle(exchange: com.sun.net.httpserver.HttpExchange) {
    render(content, exchange, 200, "text/plain")
  }
}
 
thriftJarEntries.filter(_.endsWith(".thrift")).foreach(entry => {
  val handler = new PlainTextHandler(getJarReferenceAsText("/" + entry))
  ostrichService.httpServer.createContext("/thrift/" + entry, handler)
})
```

If you don’t already have an index from your HTML documentation, a basic page linking to the Thrift files can do the job.

```scala
val thriftFiles = thriftJarEntries.filter(_.endsWith(".thrift")).seq
val linksToFiles = thriftFiles.sorted.map(f=> {
  s"""<a href="$f">$f</a>"""
})
 
//if you have a lot of files, break them into separate lines. 6 per line.
val fileHtml = linksToFiles.grouped(6).map(_.mkString(" | ")).mkString("<br/>")
 
//route the index page
ostrichService.addContext("/thrift/"){
  () => "<html><body>" + fileHtml + "</body></html>"
}
```

Now all Thrift documentation is at our fingertips, served up at _http://host:port/thrift/_

