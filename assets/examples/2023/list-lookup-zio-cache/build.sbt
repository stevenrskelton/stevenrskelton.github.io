lazy val listlookupziocache = project
  .in(file("."))
  .settings(
    name := "list-lookup-zio-cache",
    organization := "ca.stevenskelton.examples",
    description := "ZIO Cache expanded to efficiently support Seq[Key] calls.",
    version := "0.1.0",
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-cache"         % "0.2.3",
      "dev.zio" %% "zio-test"          % "2.1.4" % Test,
      "dev.zio" %% "zio-test-sbt"      % "2.1.6" % Test,
      "dev.zio" %% "zio-test-magnolia" % "2.1.6" % Test,
      "org.scalatest" %% "scalatest"   % "3.2.18"  % Test,
    ),
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      //"-indent", //"-rewrite",
      "-Wsafe-init",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Xfatal-warnings",
    ),
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
  )

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")