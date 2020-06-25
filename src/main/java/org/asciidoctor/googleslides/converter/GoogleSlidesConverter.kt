package org.asciidoctor.googleslides.converter

import org.asciidoctor.ast.ContentNode
import org.asciidoctor.ast.Document
import org.asciidoctor.converter.AbstractConverter
import org.asciidoctor.converter.ConverterFor
import org.asciidoctor.googleslides.GoogleSlidesGenerator
import org.asciidoctor.googleslides.SlideDeck
import java.io.OutputStream


@ConverterFor("googleslides")
class GoogleSlidesConverter(backend: String?, opts: Map<String?, Any?>?) : AbstractConverter<String>(backend, opts) {

  override fun convert(node: ContentNode, transform: String?, o: Map<Any, Any>): String? {
    if (node is Document) {
      return GoogleSlidesGenerator.generate(SlideDeck.from(node))
    }
    // no-op
    return null
  }

  override fun write(presentationId: String?, out: OutputStream?) {
    // no-op
  }
}
