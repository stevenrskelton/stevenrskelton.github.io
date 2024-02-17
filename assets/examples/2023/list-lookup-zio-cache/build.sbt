lazy val listlookupziocache = project
  .in(file("."))
  .settings(
    name := "list-lookup-zio-cache",
    organization := "ca.stevenskelton.examples",
    description := "",
    version := "0.1.0",
    scalaVersion := "3.3.1",
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-cache"         % "0.2.3",
      "dev.zio" %% "zio-test"          % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-sbt"      % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-magnolia" % "2.1-RC1" % Test,
      "org.scalatest" %% "scalatest"   % "3.2.15"  % Test,
    ),
  )

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")