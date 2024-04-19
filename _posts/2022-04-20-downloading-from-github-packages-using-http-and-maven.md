---
title: "Downloading from GitHub Packages using HTTP and Maven"
categories:
  - DevOps/Platform
tags:
  - SBT
  - GitHub
---

GitHub Packages is a Maven compatible repository accessible outside of GitHub. Artifacts can be downloaded
using Maven or directly through the GitHub web interface. There is no REST API available to search GitHub Packages
so this article walks through the URLs exposed for Maven which can be used to create an API with only HTTP commands.
The URLs to browse packages and download files will be covered, as well as steps to more effectively use the free tier
for private repositories.

{% include table-of-contents.html height="400px" %}

## Private Repository Free-Tier Limits

GitHub has separate pricing tiers (or caps) for private repositories. Public repos are generally free for virtually all
actions, while private repos generally have a free limited use, and beyond the limits requests are blocked unless paid
for. The private tier limits are currently:

| Artifact Storage | Data transfer within a GitHub Action | Maven traffic to GitHub Packages |
|:----------------:|:------------------------------------:|:--------------------------------:|
|      500MB       |              Unlimited               |          1GB per Month           |

Depending on the number of developers, it is usually best practice to run a local Maven repository proxy such as
[JFrog Artifactory](https://jfrog.com/artifactory/)
or [Sonotype Nexus](https://www.sonatype.com/products/sonatype-nexus-repository) which will download files once and
cache subsequent requests. This will minimize the metered transfers, but it is still quite easy to exceed 1GB/month
with frequent releases.

## Bypassing GitHub Transfer Limits

GitHub Packages has restrictive transfer limits, but alternative means exist. GitHub will allow unlimited transfers
within a GitHub Action, so transferring files during CI/CD is a practical workaround. This is written about in the
article [Data Transfers and Egress within a GitHub Action]({% post_url
2024-01-24-data-transfers-egress-github-actions %})

# GitHub Packages Maven via URL

## Browsing Available Versions

Maven operates through simple HTTP requests, and this article will document these calls such that they can be made
without Maven installed, and directly made using `wget` or `curl`.

_(All URLs in this document require an `Authorization: token GITHUB_TOKEN` HTTP header)_

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

At these URLs is XML metadata which can be used to construct all other REST URLs for the repository artifacts. An 
example of the XML is:

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

There is a special case for release versions which are a
[SNAPSHOT](https://maven.apache.org/guides/getting-started/index.html#what-is-a-snapshot-version). Maven has defined 
this functionality to accommodate rapidly evolving code such that versions can be auto-incremented on each subsequent 
publish into Maven. The *version* has the form `version-SNAPSHOT` such that `SNAPSHOT` is replaced by corresponding 
timestamp/iteration numbers on the artifacts. To resolve these values, Maven exposes the URLs:

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

This example XML has two `0.1.0-SNAPSHOT` releases: `0.1.0-20230329.004700-13` and `0.1.0-20230330.234307-29`.
These document schema expose the fields:

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

For `SNAPSHOT` releases, the version number used to download file references the `value` from this file.

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

The benefit here is the input parameter for a separate `settings.xml`. This file can be used to contain Maven 
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

## Private Repo Download Caps, what about MD5 files?

There are costs associated with outbound traffic - either directly paid for GitHub Pro accounts or indirectly with 1GB
transfer limits for private repos with free accounts. However, use-cases exist for the external access of smaller files
such as _pom_, _md5_, _sha1_ and _maven-metadata.xml_.

The _md5_ and _sha1_ are meant for use in integrity validation. In a multi-hop or cloud situation it is quite possible
intermediate storage (such as S3) poses risks as an attack vector, opportunity for partial transfers, or file
corruption. Even if GitHub servers and the final deployment server are secure, if artifacts pass though a layer with
security administered by a separate authority mistakes can happen. A final _md5_ comparison to the GitHub
Packages hosted md5 file can ensure the correct artifacts were properly copied with little complexity or performance
overhead.

<img src="/assets/images/2022/04-20/md5validation.png" alt="MD5 Validation UML" title="MD5 Validation UML" style="text-align: center;"/>

Another use is in version monitoring and publication. For Continuous Delivery pipelines that stop short of deployment
upgrading, there can be a need for version publication and monitoring. Applications or tools can directly use GitHub
Packages metadata to monitor release versions to provide update notification on projects that are not managed by a
formal package manager.

These are two practical uses of directly accessing GitHub Package files outside a GitHub Action. Neither will incur
significant use of external bandwidth, and both can provide flexibility in custom CI/CD pipelines. 
