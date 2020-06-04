/**
 *
 */
plugins {
  application
  `kotlin-dsl`
}

dependencies {
  implementation("com.google.api-client:google-api-client:1.23.0")
  implementation("com.google.oauth-client:google-oauth-client-jetty:1.23.0")
  implementation("com.google.apis:google-api-services-slides:v1-rev294-1.23.0")
  implementation("org.asciidoctor:asciidoctorj:2.2.0")
  implementation("org.jsoup:jsoup:1.13.1")
  // Align versions of all Kotlin components
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  // Use the Kotlin JDK 8 standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation("org.jetbrains.kotlin:kotlin-test")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
  testImplementation("org.assertj:assertj-core:3.11.1")
}

application {
  mainClassName = "Slides"
  applicationName = "adoc2googleslides"
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

version = "1.0"

repositories {
  mavenCentral()
}
