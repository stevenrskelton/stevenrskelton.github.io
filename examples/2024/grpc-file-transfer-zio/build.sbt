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

lazy val grpcfiletransferzio = project
  .in(file("."))
  .settings(
    name := "grpc-file-transfer-zio",
    organization := "ca.stevenskelton.examples",
    description := "File upload/download using gRPC structs.",
    version := "0.1.0",
    scalaVersion := "3.5.1",
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.grpc" %  "grpc-netty"         % "1.65.1",
      "dev.zio" %% "zio"                % "2.1.9",
      "dev.zio" %% "zio-nio"            % "2.0.2",
      "dev.zio" %% "zio-test"           % "2.1.9" % Test,
      "dev.zio" %% "zio-test-sbt"       % "2.1.9" % Test,
      "dev.zio" %% "zio-test-magnolia"  % "2.1.9" % Test,
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
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
  )
