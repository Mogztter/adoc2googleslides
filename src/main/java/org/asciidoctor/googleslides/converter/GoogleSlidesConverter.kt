package org.asciidoctor.googleslides.converter

import org.asciidoctor.ast.ContentNode
import org.asciidoctor.ast.Document
import org.asciidoctor.converter.AbstractConverter
import org.asciidoctor.converter.ConverterFor
import org.asciidoctor.googleslides.GoogleApi
import org.asciidoctor.googleslides.GoogleSlidesGenerator
import org.asciidoctor.googleslides.SlideDeck
import org.asciidoctor.jruby.ast.impl.ContentNodeImpl
import org.asciidoctor.jruby.ast.impl.PhraseNodeImpl
import org.slf4j.LoggerFactory
import java.io.OutputStream


@ConverterFor("googleslides")
class GoogleSlidesConverter(backend: String?, private val opts: Map<String?, Any?>?) : AbstractConverter<String>(backend, opts) {
  private val logger = LoggerFactory.getLogger(GoogleSlidesConverter::class.java)

  private val quoteTags = mapOf(
    "monospaced" to HtmlTag("<code>", "</code>"),
    "emphasis" to HtmlTag("<em>", "</em>"),
    "strong" to HtmlTag("<strong>", "</strong>"),
    "double" to HtmlTag("&#8220;", "&#8221;"),
    "single" to HtmlTag("&#8216;", "&#8217;"),
    "mark" to HtmlTag("<mark>", "</mark>"),
    "superscript" to HtmlTag("<sup>", "</sup>"),
    "subscript" to HtmlTag("<sub>", "</sub>")
  )

  override fun convert(node: ContentNode, transform: String?, o: Map<Any, Any>): String? {
    return when (node) {
      is Document -> {
        val credentialsPath = getCredentialsPath(node)
        val presentationId = getPresentationId(node)
        val copyId = getCopyId(node)
        val slidesService = GoogleApi.getSlidesService(credentialsPath)
        val driveService = GoogleApi.getDriveService(credentialsPath)
        GoogleSlidesGenerator.generate(SlideDeck.from(node), slidesService, driveService, presentationId, copyId)
      }
      // FIXME: should delegate to the built-in HTML5 converter... :|
      is PhraseNodeImpl -> {
        val (open, close) = quoteTags.getValue(node.type)
        "$open${node.text}$close"
      }
      else -> {
        (node as ContentNodeImpl).getString("text")
      }
    }
  }

  override fun write(presentationId: String?, out: OutputStream?) {
    // no-op
  }

  private fun getCopyId(document: Document): String? {
    logger.debug("Resolving the copy id...")
    val optionValue = resolveFromOptions("copy-id")
    if (optionValue != null) {
      return optionValue
    }
    val systemEnvValue = resolveFromSystemEnv("ASCIIDOCTOR_GOOGLE_SLIDES_COPY_ID")
    if (systemEnvValue != null) {
      return systemEnvValue
    }
    val documentAttributeValue = resolveFromDocumentAttributes("google-slides-copy-id", document)
    if (documentAttributeValue != null) {
      return documentAttributeValue
    }
    return null
  }

  private fun getPresentationId(document: Document): String? {
    logger.debug("Resolving the presentation id...")
    val optionValue = resolveFromOptions("presentation-id")
    if (optionValue != null) {
      return optionValue
    }
    val systemEnvValue = resolveFromSystemEnv("ASCIIDOCTOR_GOOGLE_SLIDES_PRESENTATION_ID")
    if (systemEnvValue != null) {
      return systemEnvValue
    }
    val documentAttributeValue = resolveFromDocumentAttributes("google-slides-presentation-id", document)
    if (documentAttributeValue != null) {
      return documentAttributeValue
    }
    return null
  }

  private fun getCredentialsPath(document: Document): String {
    logger.debug("Resolving the credentials path...")
    val optionValue = resolveFromOptions("credentials-path")
    if (optionValue != null) {
      return optionValue
    }
    val systemEnvValue = resolveFromSystemEnv("ASCIIDOCTOR_GOOGLE_SLIDES_CREDENTIALS_PATH")
    if (systemEnvValue != null) {
      return systemEnvValue
    }
    val documentAttributeValue = resolveFromDocumentAttributes("google-slides-credentials-path", document)
    if (documentAttributeValue != null) {
      return documentAttributeValue
    }
    throw IllegalArgumentException("Unable to resolve the credentials path from: " +
      "the converter option 'credentials-path', " +
      "the system environment 'ASCIIDOCTOR_GOOGLE_SLIDES_CREDENTIALS_PATH' " +
      "or the document attribute 'google-slides-credentials-path'. " +
      "The credentials file is mandatory to authenticate with the Google Slides API, aborting.")
  }

  private fun resolveFromSystemEnv(name: String): String? {
    logger.debug("Trying to resolve the value from the system environment $name...")
    val systemEnvValue = System.getenv(name)
    if (systemEnvValue == null) {
      logger.debug("$name system environment is missing")
    }
    return systemEnvValue
  }

  private fun resolveFromDocumentAttributes(name: String, document: Document): String? {
    logger.debug("Trying to resolve the value from the document attributes...")
    if (document.hasAttribute(name)) {
      return document.getAttribute(name).toString().trim()
    }
    logger.debug("$name document attribute is missing")
    return null
  }

  private fun resolveFromOptions(name: String): String? {
    logger.debug("Trying to resolve the value from the converter options...")
    val credentialsPath = opts?.get(name) as String?
    if (credentialsPath != null) {
      return credentialsPath
    }
    logger.debug("$name option is missing")
    return null
  }
}

data class HtmlTag(val open: String, val close: String)
