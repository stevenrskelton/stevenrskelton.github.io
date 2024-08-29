lazy val grpcfiletransferzio = project
  .in(file("."))
  .settings(
    name := "grpc-file-transfer-zio",
    organization := "ca.stevenskelton.examples",
    description := "File upload/download using gRPC structs.",
    version := "0.1.0",
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio"                % "2.1.6",
      "dev.zio" %% "zio-test"           % "2.1.4" % Test,
      "dev.zio" %% "zio-test-sbt"       % "2.1.6" % Test,
      "dev.zio" %% "zio-test-magnolia"  % "2.1.6" % Test,
      "dev.zio" %% "zio-test-junit"     % "2.1.5" % Test,
    ),
    javacOptions ++= Seq("-source", "11", "-target", "11"),
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
