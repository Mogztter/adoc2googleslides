import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 *
 */
plugins {
  `maven-publish`
  id("com.jfrog.bintray") version "1.8.4"
  `kotlin-dsl`
}

dependencies {
  api("org.asciidoctor:asciidoctorj:2.2.0")
  implementation("com.google.api-client:google-api-client:1.30.9")
  implementation("com.google.oauth-client:google-oauth-client-jetty:1.30.6")
  implementation("com.google.apis:google-api-services-slides:v1-rev20200611-1.30.9")
  implementation("com.google.apis:google-api-services-drive:v3-rev20200609-1.30.9")
  implementation("org.jsoup:jsoup:1.13.1")
  implementation("org.slf4j:slf4j-api:1.7.30")
  // Align versions of all Kotlin components
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  // Use the Kotlin JDK 8 standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
  testImplementation("org.assertj:assertj-core:3.11.1")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

version = "0.0.27"
group = "io.github.mogztter"

repositories {
  mavenCentral()
}

val dryRunPublications = (project.findProperty("dryRun") as String?)?.toBoolean() ?: false
val (buildDateOnly, buildTimeOnly) = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ")
  .format(ZonedDateTime.now(ZoneOffset.UTC))
  .split(" ")

val artifactName = project.name
val artifactGroup = project.group.toString()
val artifactVersion = project.version.toString()

val pomUrl = "https://github.com/Mogztter/adoc2googleslides"
val pomScmUrl = "https://github.com/Mogztter/adoc2googleslides"
val pomIssueUrl = "https://github.com/Mogztter/adoc2googleslides/issues"
val pomDesc = "https://github.com/Mogztter/adoc2googleslides"

val githubRepo = "Mogztter/adoc2googleslides"
val githubReadme = "README.md"

val pomLicenseName = "MIT"
val pomLicenseUrl = "https://opensource.org/licenses/mit-license.php"
val pomLicenseDist = "repo"

val pomDeveloperId = "Mogztter"
val pomDeveloperName = "Guillaume Grossetie"

publishing {
  publications {
    create<MavenPublication>("asciidoctor-googleslides") {
      groupId = artifactGroup
      artifactId = artifactName
      version = artifactVersion
      from(components["java"])

      pom.withXml {
        asNode().apply {
          appendNode("description", pomDesc)
          appendNode("name", rootProject.name)
          appendNode("url", pomUrl)
          appendNode("licenses").appendNode("license").apply {
            appendNode("name", pomLicenseName)
            appendNode("url", pomLicenseUrl)
            appendNode("distribution", pomLicenseDist)
          }
          appendNode("developers").appendNode("developer").apply {
            appendNode("id", pomDeveloperId)
            appendNode("name", pomDeveloperName)
          }
          appendNode("scm").apply {
            appendNode("url", pomScmUrl)
          }
        }
      }
    }
  }
}

bintray {
  user = project.findProperty("bintrayUser").toString()
  key = project.findProperty("bintrayKey").toString()
  publish = true

  setPublications("asciidoctor-googleslides")

  pkg.apply {
    repo = "io.github.mogztter"
    name = artifactName
    userOrg = "mogztter"
    githubRepo = githubRepo
    vcsUrl = pomScmUrl
    description = "Convert AsciiDoc to Google Slides"
    setLabels("asciidoc", "google-slides", "converter")
    setLicenses("MIT")
    desc = description
    websiteUrl = pomUrl
    issueTrackerUrl = pomIssueUrl
    githubReleaseNotesFile = githubReadme

    version.apply {
      name = artifactVersion
      desc = pomDesc
      released = ZonedDateTime.now(ZoneOffset.UTC).toString()
      vcsTag = artifactVersion
    }
  }
}
