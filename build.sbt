name := "examples"

version := "0.1.0-SNAPSHOT"
organization := "ca.stevenskelton"

scalaVersion := "3.4.0"

val javaVersion = "21"

lazy val scala3_aws_lambda_dynamodb_importer = RootProject(file("./assets/examples/2022/scala3-aws-lambda-dynamodb-importer"))
lazy val list_lookup_zio_cache = RootProject(file("./assets/examples/2023/list-lookup-zio-cache"))

lazy val root = (project in file("."))
  .dependsOn(scala3_aws_lambda_dynamodb_importer, list_lookup_zio_cache)
  .aggregate(scala3_aws_lambda_dynamodb_importer, list_lookup_zio_cache)
  .settings(
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-indent", //"-rewrite",
      //        "-Yexplicit-nulls",
      "-Ysafe-init",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Wnonunit-statement",
      "-Xfatal-warnings",
    ),
    Test / scalacOptions --= Seq(
      "-Wnonunit-statement",
    ),
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
  )