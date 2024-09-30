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
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc" %  "grpc-netty"         % "1.65.1",
      "dev.zio" %% "zio-cache"          % "0.2.3",
      "dev.zio" %% "zio-test"           % "2.1.4" % Test,
      "dev.zio" %% "zio-test-sbt"       % "2.1.6" % Test,
      "dev.zio" %% "zio-test-magnolia"  % "2.1.6" % Test,
      "dev.zio" %% "zio-test-junit"     % "2.1.5" % Test,
    ),
    javacOptions ++= Seq("-source", "21", "-target", "21"),
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
//      "-deprecation",
      "-feature",
      "-unchecked",
//      "-new-syntax", "-rewrite",
      "-Wsafe-init",
//      "-Wunused:all",
//      "-Wvalue-discard",
//      "-Wnonunit-statement",
//      "-Xfatal-warnings",
    ),
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
    assembly / mainClass := Some("ca.stevenskelton.examples.realtimeziohubgrpc.externaldata.performance.MainServer"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.concat
      case _ => MergeStrategy.first
    },
  )
