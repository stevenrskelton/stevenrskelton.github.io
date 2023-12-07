
name := "list-lookup-zio-cache"

version := "0.1.0-SNAPSHOT"
organization := "ca.stevenskelton"

scalaVersion := "3.3.1"

val javaVersion = "19"

lazy val listlookupziocache = (project in file("."))
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

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-cache" % "0.2.3",
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)