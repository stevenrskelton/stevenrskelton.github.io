lazy val jobqueuezioscope = project
  .in(file("."))
  .settings(
    name := "job-queue-zio-scope",
    organization := "ca.stevenskelton.examples",
    description := "Job Queue using ZIO Scope to manage removal.",
    version := "0.1.0",
    scalaVersion := "3.5.1",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"                % "2.1.6",
      "dev.zio" %% "zio-test"           % "2.1.4" % Test,
      "dev.zio" %% "zio-test-sbt"       % "2.1.6" % Test,
      "dev.zio" %% "zio-test-magnolia"  % "2.1.6" % Test,
      "dev.zio" %% "zio-test-junit"     % "2.1.5" % Test,
    ),
    javacOptions ++= Seq("-source", "21", "-target", "21"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-new-syntax", "-rewrite",
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
