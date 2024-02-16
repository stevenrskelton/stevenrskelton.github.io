name := "examples"

version := "0.1.0-SNAPSHOT"
organization := "ca.stevenskelton"

scalaVersion := "3.3.1"

val javaVersion = "19"

lazy val scala3_aws_lambda_dynamodb_importer = RootProject(file("./assets/examples/2022/scala3-aws-lambda-dynamodb-importer"))
lazy val list_lookup_zio_cache = RootProject(file("./assets/examples/2023/list-lookup-zio-cache"))

lazy val root = (project in file("."))
  .dependsOn(scala3_aws_lambda_dynamodb_importer, list_lookup_zio_cache)
  .aggregate(scala3_aws_lambda_dynamodb_importer, list_lookup_zio_cache)
  .settings(
    scalacOptions ++= {
      Seq(
        "-encoding", "UTF-8",
        "-deprecation",
        "-feature",
        "-unchecked",
        "-language:experimental.macros",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Ykind-projector",
        //        "-Yexplicit-nulls",
        "-Ysafe-init",
        "-Wvalue-discard",
//        "-source:3.0-migration",
         "-Xfatal-warnings"
      )
    },
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
  )