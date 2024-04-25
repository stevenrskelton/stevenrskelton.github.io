---
title: "Downloading from GitHub Packages using HTTP and Maven"
categories:
  - DevOps/Platform
tags:
  - SBT
  - GitHub
---

GitHub Packages is a Maven compatible repository accessible outside of GitHub. It serves as the code repository used in
Java project compilation both on workstations and within a CI/CD pipeline, as well as allowing manual file downloads
through the GitHub web interface. Because it is meant for only these 2 purposes there is no REST API available making
custom integrations more difficult than need be. This article documents the URLs exposed through Maven which can be
used to create an API of simple HTTP commands. URLs to browse packages and download files will be covered, as well as
steps to more effectively use free tier resources allowed on private repositories.

{% include table-of-contents.html height="500px" %}

# Private Repository Free-Tier Limits

GitHub has separate pricing tiers (or caps) for private repositories. Public repos are generally free for virtually all
actions, while private repos generally have a free limited use, and beyond those limits requests are blocked unless paid
for. The private tier limits are currently:

| Artifact Storage | Data transfer within a GitHub Action | Maven traffic to GitHub Packages |
|:----------------:|:------------------------------------:|:--------------------------------:|
|      500MB       |              Unlimited               |          1GB per Month           |

## Artifact Caching to Reduce Transfers

Unless paying for managed services is within budget, best practice is to run a local Maven repository proxy such as
[JFrog Artifactory](https://jfrog.com/artifactory/)
or [Sonotype Nexus](https://www.sonatype.com/products/sonatype-nexus-repository) to minimize network use. Whether
utilizing internal self-managed, or an externally managed service such as GitHub Packages when budget allows, network
use can easy to exceed 1GB/month. Frequent releases, deep dependency graphs and large number of developers all
contribute to growing this significantly. Another practical reason to choose a self-hosted repository is the increased
flexibility in managing allow/deny lists of acceptable packages. Open-source software has a solid track record of
security, but untrustworthy authors, abandoned projects, and the very nature of community contributed code make ongoing
audit the external code dependencies a necessity.

## Bypassing Transfer Limits

GitHub Packages has restrictive transfer limits, but an alternative exists. GitHub will allow unlimited transfers
within a GitHub Action, so transferring files during CI/CD is a practical workaround.

{%
include figure image_path="/assets/images/2022/05/github_egress.svg"
caption="Egress alternatives with GitHub CI/CD using GitHub Actions and GitHub Packages"
img_style="padding: 10px; background-color: white;"
%}

This is further written about in <strong>[Data Transfers and Egress within a GitHub Action]({% post_url
2024-01-24-data-transfers-egress-github-actions %})</strong>.

# GitHub Packages Maven via URL

Maven is based around an HTTP API, and all network operations are performed through simple HTTP requests. This
exposes a foundation to build a capable custom API for uses beyond Maven functionality.

## Browsing Available Versions

This article will document useful Maven URLs such that they can be made without Maven installed and directly made
using `wget` or `curl`.

_All URLs in this document require an `Authorization: token GITHUB_TOKEN` HTTP header_

Maven packaging defines 3 fields for every package:

```xml

<dependency>
    <groupId>{groupId}</groupId>
    <artifactId>{artifactId}</artifactId>
    <version>{version}</version>
</dependency>
```

Maven exposes XML files as REST URLs, and the GitHub Packages artifact URLs have the form:

```shell
https://maven.pkg.github.com/{githubUser}/{githubRepository}/{groupId}/{artifactId}/maven-metadata.xml
```

For example, a groupId of _ca.stevenskelton_ and artifactId of _http-maven-receiver-assembly_ has the URL:

```shell
https://maven.pkg.github.com/stevenrskelton/http-maven-receiver/ca/stevenskelton/http-maven-receiver-assembly/maven-metadata.xml
```

These URLs expose XML metadata which can construct all other REST URLs for the repository artifacts. An example of the
XML is:

```xml

<metadata>
    <groupId>ca.stevenskelton</groupId>
    <artifactId>http-maven-receiver-assembly</artifactId>
    <versioning>
        <latest>1.0.18</latest>
        <versions>
            <version>0.1.0-SNAPSHOT</version>
            <version>1.0.0</version>
            <version>1.0.1</version>
            <version>1.0.18</version>
        </versions>
        <lastUpdated>20240213012802</lastUpdated>
    </versioning>
</metadata>
```

The schema defines key fields:

```xml

<metadata>
    <groupId>{groupId}</groupId>
    <artifactId>{artifactId}</artifactId>
    <versioning>
        <latest>{version}</latest>
        <versions>
            <version>{version}</version>
        </versions>
        <lastUpdated>{time}</lastUpdated>
    </versioning>
</metadata>
```

### SNAPSHOT releases

There is a special case
for [SNAPSHOT](https://maven.apache.org/guides/getting-started/index.html#what-is-a-snapshot-version) release versions.
Snapshot versions accommodate rapidly evolving releases by generating auto-incremented composite version numbers on
each subsequent publish into Maven. The _version_ consists of an initial fixed number plus an iteration, with the form
`version-SNAPSHOT` where the version is fixed and the `SNAPSHOT` part creates the unique timestamp/iteration for the
release. This complexity requires an additional step to resolve all varying _SNAPSHOT_ parts, and for this Maven
exposes the URL:

```shell
https://maven.pkg.github.com/{githubUser}/{githubRepository}/{groupId}/{artifactId}/{version-SNAPSHOT}/maven-metadata.xml
```

So for version `0.1.0-SNAPSHOT` the URL would be:

```shell
https://maven.pkg.github.com/stevenrskelton/http-maven-receiver/ca/stevenskelton/http-maven-receiver-assembly/0.1.0-SNAPSHOT/maven-metadata.xml
```

An example of the XML document for this URL is:

```xml

<metadata modelVersion="">
    <groupId>ca.stevenskelton</groupId>
    <artifactId>http-maven-receiver-assembly</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <versioning>
        <snapshot>
            <timestamp>20230330.234307</timestamp>
            <buildNumber>29</buildNumber>
        </snapshot>
        <lastUpdated>20230330234315</lastUpdated>
        <snapshotVersions>
            <snapshotVersion>
                <extension>jar.md5</extension>
                <value>0.1.0-20230329.004700-13</value>
                <updated>20230329004708</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom</extension>
                <value>0.1.0-20230329.004700-13</value>
                <updated>20230329004709</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom.sha1</extension>
                <value>0.1.0-20230329.004700-13</value>
                <updated>20230329004710</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom.md5</extension>
                <value>0.1.0-20230329.004700-13</value>
                <updated>20230329004711</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>jar</extension>
                <value>0.1.0-20230330.234307-29</value>
                <updated>20230330234311</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>jar.sha1</extension>
                <value>0.1.0-20230330.234307-29</value>
                <updated>20230330234312</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>jar.md5</extension>
                <value>0.1.0-20230330.234307-29</value>
                <updated>20230330234313</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom</extension>
                <value>0.1.0-20230330.234307-29</value>
                <updated>20230330234314</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom.sha1</extension>
                <value>0.1.0-20230330.234307-29</value>
                <updated>20230330234315</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom.md5</extension>
                <value>0.1.0-20230330.234307-29</value>
                <updated>20230330234315</updated>
            </snapshotVersion>
        </snapshotVersions>
    </versioning>
</metadata>
```

This example XML has two `0.1.0-SNAPSHOT` releases:

- `0.1.0-20230329.004700-13`
- `0.1.0-20230330.234307-29`

The document schema used at this URL exposes the fields:

```xml

<metadata>
    <groupId>{groupId}</groupId>
    <artifactId>{artifactId}</artifactId>
    <version>{version}</version>
    <versioning>
        <snapshot>
            <timestamp>{YYYYmmDD.HHMMSS}</timestamp>
            <buildNumber>{iteration}</buildNumber>
        </snapshot>
        <lastUpdated>{YYYYmmDDHHMMSS}</lastUpdated>
        <snapshotVersions>
            <snapshotVersion>
                <extension>{extension}</extension>
                <value>{version-wo-SNAPSHOT}-{YYYYmmDD.HHMMSS}-{iteration}</value>
                <updated>{YYYYmmDDHHMMSS}</updated>
            </snapshotVersion>
        </snapshotVersions>
    </versioning>
</metadata>
```

This `<value>` tag provides us with the composite `version` numbers to we need to reference `SNAPSHOT` releases in other
Maven URLs based around version numbers.

## Downloading Artifacts using wget

The direct download URL for all Maven artifacts (_jar_, _sha1_, _md5_, _pom_) are generated using the `version` field
from _maven-metadata.xml_, or in the case of `SNAPSHOT` releases, the `value` field from _version/maven-metadata.xml_.

The `wget` command for a _jar_ is of the form:

```shell
wget -d --header="Authorization: token {GITHUB_TOKEN}" \
 https://maven.pkg.github.com/{githubUser}/{githubRepository}/{groupId}/{artifactId}/{version}/{artifactId}-{value}.jar
```

So for a `1.0.18` release the wget command is:

```shell
wget -d --header="Authorization: token {GITHUB_TOKEN}" \
 https://maven.pkg.github.com/stevenrskelton/http-maven-receiver/ca/stevenskelton/http-maven-receiver/1.0.18/http-maven-receiver-assembly-1.0.18.jar
```

These URLs are visible in GitHub Action logs whenever artifacts are published to GitHub Packages using Maven.

## GitHub API Does Not Expose Download URLs

It would be ideal to avoid processing XML and use the GitHub API JSON. This is not possible because the GitHub API
only exposes a surrogate identifier (which it also calls *version*) which is different from the Maven
artifact `version`.

For example, the following GitHub API URL exposes only surrogate version identifiers, which are not helpful in accessing
GitHub Packages via Maven URLs:

```shell
https://api.github.com/users/{githubUser}/packages/maven/{groupId}/{artifactId}/versions
```

Returns:

```json
[
  {
    "id": 18648727,
    "name": "{version-different-from-github-packages}",
    "url": "https://api.github.com/users/{githubUser}/packages/maven/{groupId}/{artifactId}/versions/18648727",
    "package_html_url": "https://github.com/{githubUser}/{githubRepository}/packages/1371196",
    "created_at": "2022-04-19T00:16:27Z",
    "updated_at": "2022-04-19T00:16:30Z",
    "html_url": "https://github.com/{githubUser}/{githubRepository}/packages/1371196?version={version}",
    "metadata": {
      "package_type": "maven"
    }
  }
]
```

Here `18648727` is a GitHub Packages *version* and `1371196` is a GitHub Packages *package* identifier for use in GitHub
Actions, both unused by Maven.

## Downloading Artifacts using Maven (mvn)

There are a few `mvn` plugins that can be run outside a configured project, directly from any command prompt. One is
[mvn dependency:copy](https://maven.apache.org/plugins/maven-dependency-plugin/copy-mojo.html). This maven plugin
command can be used instead of `wget` or `curl` to download a jar file:

```
mvn dependency:copy \
  -Dartifact={groupId}:{artifactId}:{version} \
  -DoutputDirectory=. \
  -DrepositoryId=github '
  --global-settings settings.xml
```

The benefit here is the input parameter for a separate _settings.xml_. This file can be used to contain Maven
credentials to repository, useful if the `wget` or `curl` command won't have access to the `GITHUB_TOKEN` or if this
command will be executed within a GitHub Action similar to how [Scala SBT Publishing to GitHub Packages]({% post_url
2022-04-17-scala-sbt-publishing-to-github-packages %}) publishes Maven artifacts using `mvn deploy:deploy-file`.

```xml

<settings>
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

# Alternative Uses of MD5 Files

The primary consumer of network traffic are large binary artifacts due to their size. Maven also is a repository for
smaller files: XML metadata and multiple hashcode associated to the artifacts. Downloading these smaller files would
have negligible contribution to network use while providing significant value.

The XML metadata forms the basis for search/indexing functionality of the binary artifacts. Its alternative uses have
been the topic of the article thus far; but the hashcode also provides interesting functional opportunities.

## Transfer Verification

At their core a hashcode such as _md5_ and _sha1_ is a quick ways to verify file contents and integrity using a small
number of bytes. In multi-hop or cloud situations, every transfer and intermediate storage poses a risk for file
corruption or file confusion. Research has shown TCP checksums will fail to
detect [errors for roughly 1 in 16 million to 10 billion packets](https://dl.acm.org/doi/10.1145/347059.347561). If MTU
is 1500 bytes, a worse case average would be undetected transfer error in every 24GB. File copy utilities typically have 
hashcode validation built in to address this vulnerability.

## Pipeline Integrity

Even when network transfers are successful, the hashcode represents an intrinsic property of the files preserved across 
file renaming. Any CI/CD pipeline configured to produce artifacts with a specific naming rather than automatically 
generated creates a possibility for version confusion. This can be seen when QA testing fails during a release; a
release candidate for version _x.x.x_ is produced, fails QA, and a second patched version of _x.x.x_ artifacts are 
created. There are now 2 separate artifacts with identical filenames in existence.

{%
include figure image_path="/assets/images/2022/04/md5_version_validation.svg"
caption="CI/CD integrity validation using GitHub Packages MD5 hashcode"
img_style="padding: 8px; background-color: white;"
%}

The root cause of version confusion is attempting to preserve branch name as artifact filename. If a _release-x.x.x_ git 
branch produces _x.x.x_ artifacts there is nothing linking artifacts to a particular commit. In addition to artifact
hashcode, another approach is to embed git sha into the artifact as another intrinsic property. In SBT, this can be done
at compile-time using SBT `sourceManaged` key. It represents a `Seq[File]` of all source-code to be compiled. It is 
straight-forward to append custom generated Scala sources, to be externally exposed as a help command-line params or 
an HTTP health check endpoint.

```scala
Compile / sourceGenerators += (Compile / sourceManaged, version, name).map {
  (sourceDirectory, version, name) =>
    val file = sourceDirectory / "SbtBuildInfo.scala"
    val gitSha = "git rev-parse HEAD".!!.trim
    IO.write(file, """package ca.stevenskelton.httpmavenreceiver
                     |object SbtBuildInfo {
                     |  val version = "%s"
                     |  val name = "%s"
                     |  val gitSha = "%s"
                     |}
                     |""".stripMargin.format(version, name, gitSha))
    Seq(file)
}.taskValue
```

## File Integrity Independent of User Authorization Zones

Another possible use for hashcode verification is as a centralized file authentication. When CI/CD, DEV and PROD are 
administered under separate user authentication paradigms, it may be easier to unify all user-permissions to a central
source. Artifact hashcodes are a secure file integrity mechanism unburdened from maintaining synchronization to user 
auth systems. Publicly available artifact hashcodes don't represent confidential information an can allowing for a
simplified implementation; though GitHub Packages would need a proxy layer working around a GITHUB_TOKEN requirement.

{%
include figure image_path="/assets/images/2022/04/md5_validation.svg"
caption="File verification across different user authentication systems"
img_style="padding: 8px; background-color: white;"
%}