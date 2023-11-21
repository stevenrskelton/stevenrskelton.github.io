---
title: "Scala (SBT) Publishing to Github Packages"
categories:
  - Platform
  - Scala
tags:
  - SBT
  - Github
---
Github Packages is a natural extension of a CI/CD pipeline created in Github Action. It currently offers repositories for Java (Maven), .Net (NuGet), Ruby (Gems), Javascript (npm), and Docker images.  
For a lot of users this can be a free private service if you can squeeze under the size limitation and are okay using OAuth keys managed in Github.

Scala artifacts are usually stored in a Maven compatible repository, so while Github Packages doesn't advertise Scala support explicitly it can still be a great fit for your project.
Maven publishing is supported by SBT in its `sbt publish` task, so let's give it a go.

## HTTP 422 Errors

There are a few nuances with configuring Github Packages in SBT, they can be done manually in your `build.sbt` or more easily using a purpose built SBT plugin like [sbt-github-packages](https://github.com/djspiewak/sbt-github-packages).  It looks like there are a lot of happy users, but sadly I couldn't get it to work.
```
[error] java.io.IOException: Error writing to server
[error] 	at java.base/sun.net.www.protocol.http.HttpURLConnection.writeRequests(HttpURLConnection.java:718)
[error] 	at java.base/sun.net.www.protocol.http.HttpURLConnection.writeRequests(HttpURLConnection.java:730)
[error] 	at java.base/sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1613)
[error] 	at java.base/sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1520)
[error] 	at java.base/java.net.HttpURLConnection.getResponseCode(HttpURLConnection.java:527)
[error] 	at java.base/sun.net.www.protocol.https.HttpsURLConnectionImpl.getResponseCode(HttpsURLConnectionImpl.java:334)
[error] 	at org.apache.ivy.util.url.BasicURLHandler.upload(BasicURLHandler.java:284)
[error] 	at org.apache.ivy.util.url.URLHandlerDispatcher.upload(URLHandlerDispatcher.java:82)
```

For an activily maintained Github project, I was sad to see an identical 4 month old open issue
[Without change the SBT file, I get a "java.io.IOException: Error writing to server" exception](https://github.com/djspiewak/sbt-github-packages/issues/48).  Even manually configuring SBT without the plugin couldn't resolve the issues, there might be an incompatibility with how my project is named or versioned that I just couldn't resolve.

## Über Jar
<sup>aka: fat jar, uber jar, or executable jar</sup>

_**Steps to publish regular library jars is [included as an appendix](#publishing-non-über-jars)**_

To complicate the issue further, I didn't just want to privately publish my standard artifacts, I want to publish an über jar.  Similar to how Docker creates an easy deploy with just 1 file, an über jar is similiar.  It is also similar to how a Java War file is a deployable package.  Normal Jar files are lean, they only contain your compiled code and publish their dependencies in a POM file.  This is great for libraries, but in the case where the jar is meant to be a standalone executable all of the dependencies will need to be included.  This is an über jar, it is like a regular jar but includes the `.class` or `.jar` of all of your code's dependencies.  It doesn't make sense to publish an über jar like it was a library, since the extra code it includes will likely have conflicts or overlaps with other libraries used in a linking project.

It does however to maintain a set of compiled releases, and here we will be using Github Packages.

There is an SBT pluging called [sbt/sbt-assembly](https://github.com/sbt/sbt-assembly) that will create an über jar, and allow it to be published to Maven.  It is a single-line add to `project/plugins.sbt` and takes zero configuration
```shell
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")
```
It should be as simple as running `sbt assembly` to create, and `sbt publish` to publish.  But like before, Github Packages is still returning **HTTP 422** on any attempy, and with this new non-standard artifact it just gets more complex.

## SBT is compiling, and Maven is publishing

Sometimes it's smart to not over complicate things. We have 2 tasks, compile and publish. It looks like _sbt-assembly_ compiles fine, but SBT is failing at publishing the artifacts.

An alternative approach would be to setup Maven to compile, and also use it to publish the Scala project like it was Java (which I would assume works fine since it is directly supported by Github).

A second alternative approach would be to keep SBT, but create an SBT task to publish using an external Maven call. Looking over the Maven documentation there is a [deploy:deploy-file](https://maven.apache.org/plugins/maven-deploy-plugin/deploy-file-mojo.html) command in Maven that can publish any file.

The command for our über jar will be something like:
```shell
mvn deploy:deploy-file \
  -Durl=https://maven.pkg.github.com/user/repo \
  -DrepositoryId=github \
  -Dfile=target/scala-2.13/project_2.13-version.jar \
  -DgroupId=com.yourcompany \
  -DartifactId=yourproject \
  -Dversion=1.0.0
```
There is an implicit file that the `repositoryId=github` parameter refers to, it expects your repository credentials for the `github` repository id to be stored in your `~/.m2/settings.xml` file.  Pleasantly there is way to manually specify where this file is located using `--settings=`, which will be important for us because our repository credentials for our Github Action are stored in Github.

_Sidenote 1: Since we are creating an über jar, the artifact generated by `sbt-assembly` is named `project-assembly` instead of `project`_

_Sidenote 2: Locally we would use a Github OAuth token for credentials, we could also use it in the Github Action but they should ideally use the token provided to the action._

## Creating a custom SBT task

SBT plugins and customization in `.sbt` and `/project/*` files are Scala code. Nothing stops you from running insanely complicated code in an SBT task, but here we have a pretty simple two step workflow.
We want to create a new task to:
- create an über jar using _sbt-assembly_
- create a `settings.xml` file
- publish the jar using a call to an external `mvn`

Since this is drop in task, put it either in `build.sbt` or its own file called `publishAssembyToGithubPackages.sbt`.  Defining a manually executed task is pretty simple in SBT:
```scala
lazy val publishAssembyToGithubPackages = taskKey[Unit]("Publish Über Jar to Github Packages")
publishAssembyToGithubPackages := {
  ...your scala code goes here...
}
```
The body of the task can be any Scala. Normally SBT is complicated by different configurations existing in different scopes, here everything we need is in the Global scope. The inputs for this task, whether run locally or inside Github Actions will be populated from environmental variables.  This is a secure way to handle OAuth tokens and prevent them from being commited to your code repo.

### Create an Über Jar Using _sbt-assembly_

This is simple, call `assembly` in SBT.  The task returns a `java.io.File` which is useful in the next steps.

### Create a `settings.xml` File

This file can be created in `/target` to keep things tidy when running this task locally. The secure contents of this file will also be populated by Maven from ENV variables, so it would be safe to commit this file into source code and skip this step. To dynamically create this file from a String defined in our .sbt file we can use SBT's `IO.write`.
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <activeProfiles>
    <activeProfile>github</activeProfile>
  </activeProfiles>
  <profiles>
    <profile>
      <id>github</id>
      <repositories>
        <repository>
          <id>github</id>
          <url>https://maven.pkg.github.com/${GITHUB_REPOSITORY}</url>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <servers>
    <server>
      <id>github</id>
      <username>${GITHUB_REPOSITORY_OWNER}</username>
      <password>${GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
``` 
The file references 3 ENV variables, which according to [Github Action documentation](https://docs.github.com/en/actions/learn-github-actions/environment-variables#default-environment-variables) are populated during execution:
- Github owner `GITHUB_REPOSITORY_OWNER`
- Github repository `GITHUB_REPOSITORY`
- Github OAuth token `GITHUB_TOKEN`

Once `settings.xml` exists, the first bit of Scala code should confirm these 3 ENV variables have been set:
```scala
val githubRepository = sys.env.get("GITHUB_REPOSITORY").getOrElse {
  throw new Exception("You must set environmental variable GITHUB_REPOSITORY, eg: owner/repository")
}
if(!sys.env.keySet.contains("GITHUB_REPOSITORY_OWNER")) { 
  throw new Exception("You must set environmental variable GITHUB_REPOSITORY_OWNER, eg: your username")
}
if(!sys.env.keySet.contains("GITHUB_TOKEN")) { 
  throw new Exception("You must set environmental variable GITHUB_TOKEN")
}
```

### Publish the Über Jar Using a Call to `mvn`

This last step will assume `mvn` is installed can can be called from the command line.  This is true for Github Actions, when running this task locally ensure that `mvn` is available in your `$PATH`.

The `name`, `organization` and `version` keys defined every `build.sbt` are suitable for most scenarios to populate the `mvn` parameters.  The `sbt-assembly` task returns the file we want to publish. In SBT, it is necessary to call `.value` on setting keys, since they need to be resolved to their current value.  Calling `assembly.value` on the `assembly` task automatically runs it ensuring the file is in our `/target` folder.
```scala
val exe = s"""mvn deploy:deploy-file
  -Durl=https://maven.pkg.github.com/$githubRepository
  -DrepositoryId=github -Dfile=${assembly.value}
  -DgroupId=${organization.value}
  -DartifactId=${name.value}-assembly
  -Dversion=${version.value}
  --settings=target/settings.xml
""".stripLineEnd

println(s"Executing shell command $exe")
import scala.sys.process._
if(exe.! != 0) throw new Exception("publishAssembyToGithubPackages failed")
```

## Github Workflow

We have populated our `publishAssembyToGithubPackages` code added it to a `publishAssembyToGithubPackages.sbt` file in the base of our project. The next step is to create a new Action in Github Actions.  It can be created through the website, or manually by creating a new file `/.github/workflows/publish-uber-assembly-to-github.yml`.  

_Sidenote: You may need to adjust your Personal Access Token permissions to check in this file using git since it requires `workflow` permissions._

The action YAML starts with the basics, a name:
```yaml
name: Publish Über Assembly to Github Packages
```
Make it a manual execution for now, this could optionally be tied into git tag events in the future:
```yaml
on:
  workflow_dispatch:
```
The `GITHUB_TOKEN` isn't populated into ENV by default, so do this now. It will also need write privileges to packages to push to Github Packages.
```yaml
env:
  GITHUB_TOKEN: ${{-"{{"-}}secrets.GITHUB_TOKEN}}  
permissions:
  contents: read
  packages: write
```
The actual job is a simple setup of checkout, java, and then calling our `publishAssembyToGithubPackages` task in SBT.
```yaml
jobs:
 build:
  runs-on: ubuntu-latest
  steps:
   - uses: actions/checkout@v3
   - name: Set up JDK 11
     uses: actions/setup-java@v3
     with:
      java-version: '11'
      distribution: 'temurin'
   - name: Publish Über Jar to Github Packages
     run: sbt publishAssembyToGithubPackages
```

And now we have working Github Package publishing of an über jar by adding only 2 files to our project.

## Downloading using wget

A separate article [Downloading from Github Packages Using HTTP and Maven]({% post_url 2022-04-20-downloading-from-github-packages-using-http-and-maven %}) has instructions on how to browse and download artifacts from Github Packages using HTTP and Maven.

## Publishing non-Über Jars

This article is a little easier since there is only 1 über jar to publish. A typical library would also want to publish a dependency pom, javadoc jar and sources jar, plus those for any subprojects.

The necessary changes for a monolithic project are very small, we will use other SBT packaging tasks instead of the `assembly` task in the plugin. There is are `mvn deploy:deploy-file` parameters to specify the pom file, and optionally source and JavaDoc jars.
```scala
val exe = s"""mvn deploy:deploy-file
  -Durl=https://maven.pkg.github.com/$githubRepository
  -DrepositoryId=github
  -Dfile=${(Compile / packageBin).value}
  -DpomFile=${(Compile / makePom).value}
  -Dsources=${(Compile / packageSrc).value}
  -Djavadoc=${(Compile / packageDoc).value}
  --settings=target/settings.xml
""".stripLineEnd
``` 
For monolithic libraries include _publishToGithubPackages.sbt_ instead of _publishAssemblyToGithubPackages.sbt_ and call _publishToGithubPackages_ instead of _publishAssemblyToGithubPackages_ in your Github Action.

{%
include downloadsources.html
src="/assets/images/2022/04-16/publish-uber-assembly-to-github.yml, /assets/images/2022/04-16/publishAssemblyToGithubPackages.sbt, /assets/images/2022/04-16/publishToGithubPackages.sbt"
%}
