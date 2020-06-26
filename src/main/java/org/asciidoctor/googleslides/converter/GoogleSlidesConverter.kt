package org.asciidoctor.googleslides.converter

import org.asciidoctor.ast.ContentNode
import org.asciidoctor.ast.Document
import org.asciidoctor.converter.AbstractConverter
import org.asciidoctor.converter.ConverterFor
import org.asciidoctor.googleslides.GoogleSlidesApi
import org.asciidoctor.googleslides.GoogleSlidesGenerator
import org.asciidoctor.googleslides.SlideDeck
import org.slf4j.LoggerFactory
import java.io.OutputStream


@ConverterFor("googleslides")
class GoogleSlidesConverter(backend: String?, private val opts: Map<String?, Any?>?) : AbstractConverter<String>(backend, opts) {
  private val logger = LoggerFactory.getLogger(GoogleSlidesConverter::class.java)
  override fun convert(node: ContentNode, transform: String?, o: Map<Any, Any>): String? {
    if (node is Document) {
      val credentialsPath = getCredentialsPath(node)
      val slidesService = GoogleSlidesApi.getService(credentialsPath)
      return GoogleSlidesGenerator.generate(SlideDeck.from(node), slidesService)
    }
    // no-op
    return null
  }

  override fun write(presentationId: String?, out: OutputStream?) {
    // no-op
  }

  private fun getCredentialsPath(document: Document): String {
    logger.debug("Trying to resolve the credentials path from the converter options...")
    val credentialsPath = opts?.get("credentials-path") as String?
    if (credentialsPath != null) {
      return credentialsPath
    }
    logger.debug("credentials-path option is missing")
    logger.debug("Trying to resolve the credentials path from the system environment ASCIIDOCTOR_GOOGLE_SLIDES_CREDENTIALS_PATH...")
    val credentialsPathSystemEnv = System.getenv("ASCIIDOCTOR_GOOGLE_SLIDES_CREDENTIALS_PATH")
    if (credentialsPathSystemEnv != null) {
      return credentialsPathSystemEnv
    }
    logger.debug("ASCIIDOCTOR_GOOGLE_SLIDES_CREDENTIALS_PATH system environment is missing")
    logger.debug("Trying to resolve the credentials path from the document attributes...")

    val credentialsPathDocumentAttribute = document.getAttribute("google-slides-credentials-path")
    if (credentialsPathDocumentAttribute != null || credentialsPathDocumentAttribute.toString().isNotBlank()) {
      return credentialsPathDocumentAttribute.toString().trim()
    }
    logger.debug("google-slides-credentials-path document attribute is missing")
    throw IllegalArgumentException("Unable to resolve the credentials path from: " +
      "the converter option 'credentials-path', " +
      "the system environment 'ASCIIDOCTOR_GOOGLE_SLIDES_CREDENTIALS_PATH' " +
      "or the document attribute 'google-slides-credentials-path'. " +
      "The credentials file is mandatory to authenticate with the Google Slides API, aborting.")
  }
}
