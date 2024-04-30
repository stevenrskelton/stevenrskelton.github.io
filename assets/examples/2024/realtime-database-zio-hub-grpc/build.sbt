Compile / PB.targets := Seq(
  scalapb.gen(
    flatPackage = false,
    javaConversions = false,
    grpc = true,
    singleLineToProtoString = true,
    asciiFormatToString = true,
    lenses = true
  ) -> (Compile / sourceManaged).value / "scalapb",
  scalapb.zio_grpc.ZioCodeGenerator -> (Compile / sourceManaged).value / "scalapb"
)

lazy val realtimeziohubgrpc = project
  .in(file("."))
  .settings(
    name := "realtime-database-zio-hub-grpc",
    organization := "ca.stevenskelton.examples",
    description := "Bi-directional GRPC using ZIO Hub.",
    version := "0.1.0",
    scalaVersion := "3.4.0",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc" %  "grpc-netty"         % "1.62.2",
      "dev.zio" %% "zio-cache"          % "0.2.3",
      "dev.zio" %% "zio-test"           % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-sbt"       % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-magnolia"  % "2.1-RC1" % Test,
      "dev.zio" %% "zio-test-junit"     % "2.1-RC1" % Test,
    ),
    javacOptions ++= Seq("-source", "17", "-target", "17"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
//      "-deprecation",
      "-feature",
      "-unchecked",
//      "-new-syntax", "-rewrite",
      "-Ysafe-init",
//      "-Wunused:all",
//      "-Wvalue-discard",
//      "-Wnonunit-statement",
//      "-Xfatal-warnings",
    ),
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
  )
