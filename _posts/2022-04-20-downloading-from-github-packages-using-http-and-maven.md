---
title: "Downloading from GitHub Packages using HTTP and Maven"
categories:
  - Platform
tags:
  - SBT
  - GitHub
---

GitHub Packages is a Maven compatible repository and is accessible independent of GitHub. This article expands on
alternative access to these files through direct download URLs and HTTP browsing using Maven metadata, and how to
effectively use the free tier for private repositories.

{% include table-of-contents.html height="200px" %}

GitHub generally has separate pricing tiers (or caps) depending on a repo being set to public or private. Public 
repos are generally free, and private repos have a minimal use which is free, and exceeding these limits requires 
payment. The free tier is currently:

| Artifact Storage | Data Transfer Out within a GitHub Action | Data Transfer Out outside of a GitHub Action |
|:----------------:|:----------------------------------------:|:--------------------------------------------:|
|      500MB       |                Unlimited                 |                1GB per Month                 |

Using GitHub Packages as a Maven repository is best done through a proxy (such as Artifactory or Nexus) to cache
files and minimize external data transfer from GitHub Packages.

Files can be downloaded using Maven as part of the typical developer setup as well as through the GitHub website, 
but in this article we will examine a third use-case, accessing files directly using `wget` or `curl`.

### Bypassing GitHub Limits

This article examines how to read from GitHub Packages, which counts against data transfer out quotas.  A keen eye 
will see that data transfer out within a GitHub Action is unmetered, this is investigated and practically applied 
in a separate article [Data Transfers within a GitHub Action]({% post_url
2024-01-24-data-transfers-github-actions %})

## Browsing Available Versions in GitHub Packages

The standard Maven packaging defines 3 key fields for every package:

```xml
<dependency>
    <groupId>{groupId}</groupId>
    <artifactId>{artifactId}</artifactId>
    <version>{version}</version>
</dependency>
```

These files are used in the paths for a Maven repository, on GitHub Packages the URL for artifacts has the form:

```shell
https://maven.pkg.github.com/{githubUser}/{githubRepository}/{groupId}/{artifactId}/maven-metadata.xml
```

_(All URLs in this document require an `Authorization: token GITHUB_TOKEN` HTTP header)_

These URL will return an XML document containing the Maven repository metadata for all available artifacts of this
package, and it will be of the form:

```xml

<metadata>
    <groupId>{groupId}</groupId>
    <artifactId>{artifactId}</artifactId>
    <versioning>
        <latest>{version}</latest>
        <versions>
            <version>{version}</version>
            ...more..
        </versions>
        <lastUpdated>20220419001636</lastUpdated>
    </versioning>
</metadata>
```

To generate the file URLs for a specific version there is another XML document available at:

```shell
https://maven.pkg.github.com/{githubUser}/{githubRepository}/{groupId}/{artifactId}/{version}/maven-metadata.xml
```

That will list all published builds, and be something like:

```xml

<metadata modelVersion="">
    <groupId>{groupId}</groupId>
    <artifactId>{artifactId}</artifactId>
    <version>{version}</version>
    <versioning>
        <snapshot>
            <timestamp>20220419.001622</timestamp>
            <buildNumber>1</buildNumber>
        </snapshot>
        <lastUpdated>20220419001637</lastUpdated>
        <snapshotVersions>
            <snapshotVersion>
                <extension>jar</extension>
                <value>{value}</value>
                <updated>20220419001630</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>jar.sha1</extension>
                <value>{value}</value>
                <updated>20220419001632</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>jar.md5</extension>
                <value>{value}</value>
                <updated>20220419001633</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom</extension>
                <value>{value}</value>
                <updated>20220419001634</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom.sha1</extension>
                <value>{value}</value>
                <updated>20220419001635</updated>
            </snapshotVersion>
            <snapshotVersion>
                <extension>pom.md5</extension>
                <value>{value}</value>
                <updated>20220419001637</updated>
            </snapshotVersion>
            ...more...
        </snapshotVersions>
    </versioning>
</metadata>
```

## Downloading Artifacts using wget

Note the existance of a new field `value` in the GitHub Packages `maven-metadata.xml`. This field is different
than `version` in the Maven publication. In Maven, a version can have multiple `value` representing each build.

The direct download URL for all Maven artifacts (`jar`, `sha1`, `md5`, `pom`) are generated using the `value` field in
the _version/maven-metadata.xml_.

It follows that a downloadable URL and `wget` command for a `jar` would be:

```shell
wget -d --header="Authorization: token {GITHUB_TOKEN}" \
 https://maven.pkg.github.com/{githubUser}/{githubRepository}/{groupId}/{artifactId}/{version}/{artifactId}-{value}.jar
```

This URL is also visible in the logs for the GitHub Action which initially uploaded the artifacts to GitHub Packages.

## GitHub API Does Not Expose Download URLs

It would be ideal to avoid processing XML and use the more convinient JSON exposed by GitHub API. However GitHub API
only expose a second GitHub Packages identifier called *version* and this is different than the Maven
artifact `versionId`. This second *version* is generated by GitHub Packages as a unique identifier within GitHub
Packages and while useful for interacting with packages within GitHub Actions it is not useful for determining URLs.

```shell
https://api.github.com/users/{githubUser}/packages/maven/{groupId}/{artifactId}/versions
```

Returns:

```json
[
  {
    "id": 18648727,
    "name": "{version}",
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
Actions.

## Downloading Artifacts using Maven (mvn)

There are a few `mvn` plugins that do not require a Maven project to be configured, and one
is [mvn dependency:copy](https://maven.apache.org/plugins/maven-dependency-plugin/copy-mojo.html). This maven plugin can
be used instead of `wget` or `curl` to download a jar file, an example command would be:

```
mvn dependency:copy \
  -Dartifact={groupId}:{artifactId}:{version} \
  -DoutputDirectory=. \
  -DrepositoryId=github '
  --global-settings settings.xml
```

The benefit here is that this will take advantage of a separate `settings.xml` containing the credentials to
the `github` repository, useful if the `wget` or `curl` command won't have access to the `GITHUB_TOKEN` or if this
command will be executed within a GitHub Action similiar to how [Scala SBT Publishing to GitHub Packages]({% post_url
2022-04-17-scala-sbt-publishing-to-github-packages %}) publishes Maven artifacts using `mvn deploy:deploy-file`.

## Private Repo Download Caps, what about MD5 files?

There are costs associated with outbound traffic - either directly paid for Github Pro accounts or indirectly with 1GB
transfer limits for private repos with free accounts. However use-cases exist for the external access of smaller files
such as `pom`, `md5`, `sha1` and `maven-metadata.xml`.

The `md5` and `sha1` are meant for use in integrity validation. In a multi-hop or cloud situation it is quite possible
intermediate storage (such as S3) poses risks as an attack vector, opportunity for partial transfers, or file
corruption. Even if Github servers and the final deployment server are secure, if artifacts pass though a layer with
security administered by a separate authority mistakes can happen. A final `md5` comparision directly to the Github
Packages hosted md5 file can ensure the correct artifacts were properly copied with little complexity or performance
overhead.

<img src="/assets/images/2022/04-20/md5validation.png" alt="MD5 Validation UML" title="MD5 Validation UML" style="text-align: center;"/>

Another use is in version monitoring and publication. For Continuous Delivery pipelines that stop short of deployment
upgrading, there can be a need for version publication and monitoring. Applications or tools can directly use Github
Packages metadata to monitor release versions to provide update notification on projects that are not managed by a
formal package manager.

These are two practical uses of directly accessing Github Package files outside of a Github Action. Neither will incure
significant use of external bandwidth, and both can provide flexibility in custom CI/CD pipelines. 
