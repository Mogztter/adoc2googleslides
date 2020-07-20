package org.asciidoctor.googleslides

import java.nio.file.Paths
import kotlin.test.*

@Ignore("Integration tests")
class GoogleSlidesGeneratorTest {

  // IMPORTANT: configure the values below before running the tests!
  private val credentialsPath = Paths.get("", "tokens", "credentials.json").toAbsolutePath().toString()
  private val masterPresentationId = "1aR6dlFmu6ssBuf1yQJm8I_cfTOOpSBHIfjqCQzuDsdQ"

  @Test
  fun should_get_master_presentation() {
    val slidesService = GoogleApi.getSlidesService(credentialsPath)
    val driveService = GoogleApi.getDriveService(credentialsPath)
    val googleSlidesPresentation = GoogleSlidesGenerator.getPresentation(driveService, slidesService, "", masterPresentationId, null)
    val layouts = googleSlidesPresentation.layouts
    println("Layouts available: ${layouts.map { it.layoutProperties.name + " (${it.layoutProperties.displayName})" }} for presentation id: ${googleSlidesPresentation.presentationId}")
  }
}
