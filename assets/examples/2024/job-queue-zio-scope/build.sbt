lazy val jobqueuezioscope = project
  .in(file("."))
  .settings(
    name := "job-queue-zio-scope",
    organization := "ca.stevenskelton.examples",
    description := "Job Queue using ZIO Scope to manage removal.",
    version := "0.1.0",
    scalaVersion := "3.4.0",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"                % "2.1-RC1",
      "dev.zio" %% "zio-test"           % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-sbt"       % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-magnolia"  % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-junit"     % "2.1-RC1" % Test,
    ),
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      //      "-new-syntax", "-rewrite",
      "-Ysafe-init",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Xfatal-warnings",
    ),
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
  )