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
}

application {
    mainClassName = "Slides"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

version = "1.0"

repositories {
    mavenCentral()
}
