package org.asciidoctor.googleslides

import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import java.io.File
import kotlin.test.*

@Ignore("Integration tests")
class ConverterTest {

  private val asciidoctor = Asciidoctor.Factory.create()
  // IMPORTANT: configure the values below before running the tests!
  private val credentialsPath = "/path/to/credentials.json"
  private val masterPresentationId = "1aR6dlFmu6ssBuf1yQJm8I_cfTOOpSBHIfjqCQzuDsdQ"

  @Test
  fun should_convert_using_master_slide() {
    val file = File(ConverterTest::class.java.getResource("/simple-presentation.adoc").toURI())
    val result = asciidoctor.convertFile(file, OptionsBuilder.options()
      .backend("googleslides")
      .attributes(
        AttributesBuilder.attributes()
          .attribute("google-slides-copy-id", masterPresentationId)
          .attribute("google-slides-credentials-path", credentialsPath)
      ))
  }

  @Test
  fun should_convert_using_master_slide_and_custom_layout() {
    val file = File(ConverterTest::class.java.getResource("/custom-layout.adoc").toURI())
    val result = asciidoctor.convertFile(file, OptionsBuilder.options()
      .backend("googleslides")
      .attributes(
        AttributesBuilder.attributes()
          .attribute("google-slides-copy-id", masterPresentationId)
          .attribute("google-slides-credentials-path", credentialsPath)
          .attribute("google-slides-layout-quiz", "TITLE_ONLY_1")
      ))
  }
}
