//https://www.stevenskelton.ca/scala-sbt-publishing-to-github-packages/

lazy val publishAssemblyToGitHubPackages = taskKey[Unit]("Publish Über Jar to GitHub Packages")
publishAssemblyToGitHubPackages := {
  val githubRepository = sys.env.get("GITHUB_REPOSITORY").getOrElse(throw new Exception("You must set environmental variable GITHUB_REPOSITORY, eg: owner/repository"))
  if(!sys.env.keySet.contains("GITHUB_REPOSITORY_OWNER")) throw new Exception("You must set environmental variable GITHUB_REPOSITORY_OWNER, eg: your username")
  if(!sys.env.keySet.contains("GITHUB_TOKEN")) throw new Exception("You must set environmental variable GITHUB_TOKEN")

  val settingsXMLFile = new File("target/settings.xml")
  if(!settingsXMLFile.exists){
    println("File missing, creating settings.xml")
    val settingsXML = """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
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
    </settings>"""
    IO.write(settingsXMLFile, settingsXML)
  }

  val exe = s"""mvn deploy:deploy-file
    -Durl=https://maven.pkg.github.com/$githubRepository
    -DrepositoryId=github
    -Dfile=${assembly.value}
    -DgroupId=${organization.value}
    -DartifactId=${name.value}-assembly
    -Dversion=${version.value}
     --settings=target/settings.xml
  """.stripLineEnd

  println(s"Executing shell command $exe")
  import scala.sys.process._
  if(exe.! != 0) throw new Exception("publishAssemblyToGitHubPackages failed")
}
