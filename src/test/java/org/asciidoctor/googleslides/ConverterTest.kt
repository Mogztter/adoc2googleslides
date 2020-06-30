package org.asciidoctor.googleslides

import org.asciidoctor.Asciidoctor
import kotlin.test.Test
class ConverterTest {

  private val asciidoctor = Asciidoctor.Factory.create()

  @Test
  fun should_convert_using_master_slide() {

    val document = asciidoctor.convertFile(SlideContentTest::class.java.getResource("/list-items-with-inline-styles.adoc").readText(), mapOf())
    asciidoctor.convert()
    // 1vSe647hywMiYVFMhyB7VvXPnla6MEQCPTN516eJBx78
  }
}
