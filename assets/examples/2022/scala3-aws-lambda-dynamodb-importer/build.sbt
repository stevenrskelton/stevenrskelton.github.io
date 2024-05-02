lazy val scala3awslambdadynamodbimporter = project
  .in(file("."))
  .settings(
    name := "scala3-aws-lambda-dynamodb-importer",
    organization := "ca.stevenskelton.examples",
    description := "Lambda function that inserts new items into a DynamoDB table",
    version := "0.1.0",
    scalaVersion := "3.4.1",
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.2",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.5",
      "software.amazon.awssdk" % "dynamodb" % "2.25.35",
      "org.scalameta" %% "munit" % "0.7.29" % Test,
    ),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs@_*) => MergeStrategy.concat
      case PathList("META-INF", xs@_*) => MergeStrategy.discard
      case x => MergeStrategy.first
    }
  )
