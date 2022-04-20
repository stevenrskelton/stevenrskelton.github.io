---
title: "Direct Downloading from Github Packages (Maven)"
categories:
- Platform
- Scala
published: false
---
Github Packages is a natural extension of a CI/CD pipeline created in Github Action. It currently offers repositories for Java (Maven), .Net (NuGet), Ruby (Gems), Javascript (npm), and Docker images.  
For a lot of users this can be a free private service if you can squeeze under the size limitation and are okay using OAuth keys managed in Github.



## Downloading using wget

In the Github repository package page, for SNAPSOT artifacts there will a list of _Assets_ such as:
```
yourproject-0.1.0-20220417.184322-1.jar
```
Which correspond to the indicated artifact, such as:
```
<dependency>
  <groupId>com.yourcompany</groupId>
  <artifactId>yourproject</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency> 
```
These can be downloaded from the Github web page, or directly using a Maven URL of the form:
```
wget -d --header="Authorization: token {GITHUB_TOKEN}" \
 "https://maven.pkg.github.com/{user}/{repository}/{groupId}/{artifactId}/{version}/yourproject-0.1.0-20220417.184322-1.jar"
```
The exact URL will be in the Github Action logs.

```
mvn dependency:copy \
  -Dartifact=org.scalariform:scalariform_2.9.1-SNAPSHOT:0.1.1 \
  -DoutputDirectory=. \
  -DrepositoryId=github '
  --global-settings settings.xml
```
