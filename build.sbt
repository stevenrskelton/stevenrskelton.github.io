name := "examples"

version := "0.1.0-SNAPSHOT"
organization := "ca.stevenskelton"

scalaVersion := "3.5.0"

val javaVersion = "21"

lazy val scala3_aws_lambda_dynamodb_importer = RootProject(file("./examples/2022/scala3-aws-lambda-dynamodb-importer"))
lazy val list_lookup_zio_cache = RootProject(file("./examples/2023/list-lookup-zio-cache"))
lazy val realtime_database_zio_hub_grpc = RootProject(file("./examples/2024/realtime-database-zio-hub-grpc"))
lazy val job_queue_zio_scope = RootProject(file("./examples/2024/job-queue-zio-scope"))
lazy val grpc_file_transfer_zio = RootProject(file("./examples/2024/grpc-file-transfer-zio"))

lazy val root = (project in file("."))
  .dependsOn(
    scala3_aws_lambda_dynamodb_importer,
    list_lookup_zio_cache,
    realtime_database_zio_hub_grpc,
    job_queue_zio_scope,
    grpc_file_transfer_zio
  )
  .aggregate(
    scala3_aws_lambda_dynamodb_importer,
    list_lookup_zio_cache,
    realtime_database_zio_hub_grpc,
    job_queue_zio_scope,
    grpc_file_transfer_zio
  )
  .settings(
    scalacOptions ++= Seq(
      "-encoding", "UTF-8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-indent", //"-rewrite",
      //        "-Yexplicit-nulls",
      "-Wsafe-init",
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