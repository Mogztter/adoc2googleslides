package org.asciidoctor.googleslides.converter

import org.asciidoctor.Asciidoctor
import org.asciidoctor.jruby.converter.spi.ConverterRegistry

class GoogleSlidesConverterRegistry : ConverterRegistry {
  override fun register(asciidoctor: Asciidoctor) {
    asciidoctor.javaConverterRegistry().register(GoogleSlidesConverter::class.java, "googleslides")
  }
}
