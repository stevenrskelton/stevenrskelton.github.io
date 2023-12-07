name := "examples"

version := "0.1.0-SNAPSHOT"
organization := "ca.stevenskelton"

scalaVersion := "3.3.1"

val javaVersion = "19"

lazy val listlookupziocache = RootProject(file("./assets/examples/2023/list-lookup-zio-cache"))

lazy val root = (project in file("."))
  .dependsOn(listlookupziocache)
  .aggregate(listlookupziocache)
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
//        "-Wvalue-discard",
//        "-source:3.0-migration",
        // "-Xfatal-warnings"
      )
    },
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion),
  )