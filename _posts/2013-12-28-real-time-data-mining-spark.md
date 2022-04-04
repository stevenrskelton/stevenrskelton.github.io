---
#layout: post
title: "Real-time Data Mining with Spark"
categories:
  - Scala
---

There are 2 new principles at the vanguard of today’s technology:
- [Reactive UX](http://www.reactivemanifesto.org/). As the world’s population spends an increasing portion of their lives electronically, it’s becoming more and more important for businesses to capture the online audience. Web 2.0 is now over a decade old: the age of the static website is gone. UI advancements of HTML5, CSS, and a new breed of high performance JavaScript engines are bringing native app experiences to the browser.
- [Big Data](http://en.wikipedia.org/wiki/Big_data) analytics. Business needs have increased in complexity beyond simple Business Intelligence (BI) aggregates. To separate one business from the rest it’s becoming increasing important to find the needle in a growing haystack.

{% include postlogo.html title="Spark" src="/assets/images/2013/12/spark.png" %}
Apache SparkToday’s web users expect a Reactive UX, just as today’s business analysts expect Big Data functionality. One of today’s hottest fields for R&D lies in their intersection. There are few software packages optimized for this purpose, perhaps the best originated in [UC Berkeley’s AMPLab](https://amplab.cs.berkeley.edu/), and it’s called Spark.

Reactive UX
Real-time Data Mining
Big Data

### Reactive Real-Time Big Data Mining and Analysis

<svg version="1.0" width="200" height="200" style="float:right;">
<g transform="scale(0.5)">
  <circle cx="175" cy="150" r="145" style="fill:#de7676;fill-opacity:0.5;stroke:black;stroke-width:1;"></circle>
  <circle cx="250" cy="250" r="145" style="fill:#76a4de;fill-opacity:0.5;stroke:black;stroke-width:1;"></circle>
  <text x="175" y="75" style="font-size:24px;font-weight:bold;text-align:center;text-anchor: middle;fill:black;fill-opacity:0.8;font-family:Tahoma">
    <tspan>Reactive UX</tspan>
  </text>
  <text y="190" style="font-size:24px;font-weight:bold;text-align:center;text-anchor: middle;fill:black;fill-opacity:0.8;font-family:Tahoma">
    <tspan x="215">Real-time</tspan>
    <tspan x="215" dy="1.2em">Data Mining</tspan>
  </text>
  <text x="250" y="345" style="font-size:24px;font-weight:bold;text-align:center;text-anchor: middle;fill:black;fill-opacity:0.8;font-family:Tahoma">
    <tspan>Big Data</tspan>
  </text>
</g>
</svg>

As big data is already pushing today’s hardware capacity to its limits, building a low-latency yet highly interactive reactive user interface presents a technological challenge. [Apache Spark](http://spark.incubator.apache.org/) is an open source cluster computing system aimed to make data analytics fast. Spark is still in Apache incubation, so even though it is deployed in production at a lot of [companies such as Yahoo!](https://cwiki.apache.org/confluence/display/SPARK/Powered+By+Spark), releases and install processes can be a little unpolished and volatile. The Spark framework fully encapsulates network and resource management, exposing only an interface nearly identical to the standard Scala collection operators. The low level, functional user interface is efficiently translated by Spark into distributed jobs across all nodes, achieving high performance through horizontal scalability.

### Eclipse Setup

As of Dec 2013, Spark is at version 0.8.1, and the master branch compiles to Scala 2.9.3. Users wishing to develop their applications in Scala 2.10 cannot cannot rely on Maven for pre-compiled artifacts; fortunately there is partial support through a pre-production Scala-2.10 branch.

```
git pull https://github.com/apache/incubator-spark.git scala-2.10
```

Following the README, compiling is as simple as sbt assembly, resulting in a 84MB spark-assembly-0.9.0-incubating-SNAPSHOT-hadoop1.0.4.jar artifact in ./assembly/target/scala-2.10/.

A new project using Spark can be started by copying the Spark jar to the lib folder of an empty SBT project. This jar contains all its external libraries (such as Akka), so the only managed libraries most users will need to add are project specific or testing harnesses such as Specs2 and JUnit. The Eclipse project files should be generated using the SBT plugin, add

```
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.3.0")
```
to project/build.sbt, and run sbt eclipse.

### Spark Context

Starting Spark locally is almost trivial, users wishing to deploy Spark to a cluster may need to refer to the well written documentation. Once correct functionality is confirmed, most users will want to change the default log level to be less verbose as Spark in debug can be quite noisy about its task parallelization.

```scala
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.log4j.{ LogManager, Level }
 
object Main extends App {
  //LogManager.getRootLogger().setLevel(Level.WARN)
  val sc = new SparkContext("local", "Main")
 
  //TODO: Load data in sc
  //TODO: program logic
}
```

The SparkContext is used to create all RDDs, and RDD data is loaded line by line from either local or HDFS files.

### Data Models

A good data set for demos is the freely distributable Stack Exchange data dump. It consists of a few tables with simple foreign key relationships, each dumped to a separate XML file. As Stack Exchange is a family of web sites, the full data dump contains multiple sets of varying sizes, each adhering to the same schema. Some of the less popular Stack Exchange sites have data sets only a few MB in size making them ideal for unit tests and development, while Stack Exchange’s premier site Stack Overflow can be used for deployment testing boasting 30GB of data.

The data files can be easily parsed, they consist of a single collection of <row> XML nodes representing a single table’s rows. Once they are downloaded and copied locally to our project’s /data directory let’s define a case class for the Post table.

```scala
case class Post(
  id: Int,
  postTypeId: Int,
  acceptedAnswerId: Int,
  creationDate: Long,
  score: Int,
  viewCount: Int,
  body: String,
  ownerUserId: Int,
  lastActivityDate: Long,
  title: String,
  tags: Array[String],
  answerCount: Int,
  commentCount: Int,
  favoriteCount: Int,
  communityOwnedDate: Long) 
```

We’ll use companion objects to perform the XML load functionality. Each table will require an XML parser, but common behaviour, such as parsing Dates or iterating rows can be generalized in a parent class. Spark has its own mechanisms for loading files, line by line so we don’t need a separate stream parser, but we should still take advantage of Scala’s XML functionality provided by the scala.xml package to parse our data rows.

```scala
import java.io.File
import scala.io.{ BufferedSource, Source }
 
abstract class StackTable[T] {
 
  val file: File
 
  def getDate(n: scala.xml.NodeSeq): Long = n.text match {
    case "" => 0
    case s => dateFormat.parse(s).getTime
  }
 
  def dateFormat = {
    import java.text.SimpleDateFormat
    import java.util.TimeZone
    val f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    f.setTimeZone(TimeZone.getTimeZone("GMT"))
    f
  }
 
  def getInt(n: scala.xml.NodeSeq): Int = n.text match {
    case "" => 0
    case x => x.toInt
  }
 
  def parseXml(x: scala.xml.Elem): T
 
  def parse(s: String): Option[T] =
    if (s.startsWith("  <row ")) Some(parseXml(scala.xml.XML.loadString(s)))
    else None
}
```
      
The abstract file val and parseXml method must be implemented individually for each table.
It’s important to notice that parse returns an Option[T], because not all lines represent rows in the table. The first 2 lines, and last line of each file are the XML Declaration and opening/closing of the root node.

The Post companion object uses scala.xml.Elem to match XML attributes to the case class fields. Any additional data massaging, such as parsing the Tags is also performed by this class.

```scala
import scala.xml.{ NodeSeq, MetaData }
import java.io.File
import scala.io.{ BufferedSource, Source }
 
object Post extends StackTable[Post] {
 
  val file = new File("data/Posts.xml")
  assert(file.exists)
 
  override def parseXml(x: scala.xml.Elem): Post = Post(
    getInt(x \ "@Id"),
    getInt(x \ "@PostTypeId"),
    getInt(x \ "@AcceptedAnswerId"),
    getDate(x \ "@CreationDate"),
    getInt(x \ "@Score"),
    getInt(x \ "@ViewCount"),
    (x \ "@Body").text,
    getInt(x \ "@OwnerUserId"),
    getDate(x \ "@LastActivityDate"),
    (x \ "@Title").text,
    getTags(x \ "@Tags"),
    getInt(x \ "@AnswerCount"),
    getInt(x \ "@CommentCount"),
    getInt(x \ "@FavoriteCount"),
    getDate(x \ "@CommunityOwnedDate"))
 
  def getTags(x: scala.xml.NodeSeq): Array[String] = x.text match {
    case "" => Array()
    case s => s.drop(1).dropRight(1).split("><")
  }
}
```

### Resilient Distributed Datasets (RDDs)

The SparkContext has been architected to load RDDs from files, let’s modify the Main class’ SparkContext sc to load our Post file.
Once it is loaded into an RDD[String], we can flatMap using our parse: String => Option[T] method.

```scala
val minSplits = 1
val jsonData = sc.textFile(Post.file.getAbsolutePath, minSplits)
val objData = jsonData.flatMap(Post.parse)
objData.cache
var query: RDD[Post] = objData
```

Calling an RDD’s cache method will tell Spark to try and keep this dataset in memory. This is especially important here, otherwise Spark would reload the RDD from our text files on every query.

At this point, we have loaded our Stack Overflow data into separate RDDs for each table. The operations on RDDs are well documented on the Spark website, and let’s postpone an example until we get our command line data mining up and running.

### Command Console

The general objective of this application is to be able to execute a variety of commands and measure their performance. This can easily be done by reading lines of the Console, and matching them to different RDD operations.

In our Main class, let’s build an loop to handle our input.

```scala
println("Enter new command:")
do {
} while (readCommand)
println("Exit")
 
def readCommand: Boolean = {
  val command = readLine
  if (command.isEmpty) false
  else {
    //TODO: match commands
    true
  }
}
```

It will also be handy to time operations – all Scala developers should be quite familiar with the following snippet:

```scala
def time[T](name: String)(block: => T): T = {
  val startTime = System.currentTimeMillis
  val result = block // call-by-name
  println(s"$name: ${System.currentTimeMillis - startTime}ms")
  result
}
```

The syntax and tokens to execute commands in our readCommand need not be too complicated. Let’s use *:<params> to match additional filters, and !* to execute commands. For example, if we wanted to filter Posts to contain any of the tags: (“discussion”, “design”), and have a creation date within the range (2013-01-01,2014,01,01) we would expect to be able to write:

```
t:discussion,design
d:2013-01-01,2014-01-01
!
```

And it should return the number of posts, including the time it took to execute.
Let’s go ahead and fill in the `readCommand` match statement:

```scala
command match {
  case c if c.startsWith("t:") => {
    val tags = c.drop(2).split(",").toSet
    query = query.filter(_.tags.exists(tags.contains))
  }
  case c if c.startsWith("d:") => {
    val d = c.drop(2).split(",").map(i => Post.dateFormat.parse(i + "T00:00:00.000").getTime)
    query = query.filter(n => n.creationDate >= d(0) && n.creationDate < d(1))
  }
  case "!" => time("Count") {
    println(query.count)
  }
  case "~" => query = objData
}
```

At this point, our Console data mining is operational, albeit with very basic functionality. You will notice that the second time ! is executed, the query is much faster since the second time around the RDD should already be cached in RAM. When Spark initializes, you will see a line in the log:

```
INFO storage.BlockManagerMasterActor$BlockManagerInfo: registering block manager localhost:49539 with 1194.6MB)
```

If the RDD is large than the free memory (in this case 1194MB), it will be automatically paged to disk. When this occurs it will show as

```
INFO spark.CacheManager: Partition rdd_*_* not found, computing it.
```

The goal is to keep all RDDs cached in memory whenever possible so to avoid paging we will need raise the maximum RAM which the Java VM process is allowed to use. In Eclipse, this can be done on the Run As->Run Configurations… screen. Under the Arguments tab, there is a VM arguments text area. Put in -Xmx6096m, where 6096m is the number of MB to allow Spark to use – this should be chosen to be close to the amount of RAM on your machine. When deployed, this can be done by exporting `JAVA_OPTS="-Xmx6096m"`.

{%
  include downloadsources.html
  src="https://github.com/stevenrskelton/Blog/blob/master/src/main/scala/Real-Time-Data-Mining-With-Spark.scala"
%}