package org.asciidoctor.googleslides

import org.asciidoctor.ast.Document
import org.asciidoctor.ast.ListItem
import org.asciidoctor.ast.Section
import org.asciidoctor.ast.StructuralNode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.slf4j.LoggerFactory
import java.net.URL
import javax.imageio.ImageIO

data class SlideDeck(val title: String, val slides: List<Slide>) {

  companion object {
    private val logger = LoggerFactory.getLogger(SlideDeck::class.java)

    fun from(document: Document): SlideDeck {
      flattenDocument(document)
      val slides = document.blocks.mapNotNull { block ->
        if (block is Section) {
          val slideTitle = if (block.title == "!") {
            null
          } else {
            // FIXME: extract text transformation + range
            val doc = Jsoup.parseBodyFragment(block.title)
            doc.text()
          }
          val slideBlocks = block.blocks
          val (speakerNotesBlocks, contentBlocks) = slideBlocks.partition { it.roles.contains("notes") }
          val speakerNotes = speakerNotesBlocks.joinToString("\n") {
            val html = it.content as String
            val doc = Jsoup.parseBodyFragment(html)
            doc.text()
          }
          if (contentBlocks.size == 1 && contentBlocks.first().roles.contains("two-columns")) {
            val twoColumnsBlock = contentBlocks.first()
            if (twoColumnsBlock.blocks.size == 2) {
              val leftBlock = twoColumnsBlock.blocks[0]
              val rightBlock = twoColumnsBlock.blocks[1]
              val rightColumnContents = SlideContent.from(rightBlock)
              val leftColumnContents = SlideContent.from(leftBlock)
              TitleAndTwoColumns(
                title = slideTitle,
                rightColumn = rightColumnContents,
                leftColumn = leftColumnContents,
                speakerNotes = speakerNotes + rightColumnContents.speakerNotes.orEmpty() + leftColumnContents.speakerNotes.orEmpty()
              )
            } else {
              logger.warn("A two-columns block must have exactly 2 nested blocks, ignoring.")
              null
            }
          } else {
            val slideContents = contentBlocks.map { SlideContent.from(it) }
            val contents = if (slideContents.isNotEmpty()) {
              slideContents.reduce { slide, acc ->
                SlideContents(slide.contents + acc.contents, slide.speakerNotes.orEmpty() + acc.speakerNotes.orEmpty())
              }
            } else {
              SlideContents(emptyList())
            }
            TitleAndBodySlide(slideTitle, contents, speakerNotes + contents.speakerNotes)
          }
        } else {
          null
        }
      }
      return SlideDeck(document.doctitle, slides)
    }

    private fun flattenDocument(document: Document) {
      document.findBy(mapOf("context" to ":section")).filter {
        it.level > 1
      }.reversed().forEach { section ->
        var parentSection = (section.parent as Section)
        parentSection.blocks.remove(section)
        while (parentSection.parent != null && parentSection.parent.context == ":section") {
          parentSection = parentSection.parent as Section
        }
        document.blocks.add(parentSection.index + 1, section)
      }
    }
  }
}

sealed class Slide(open val title: String?, open val speakerNotes: String? = null)
sealed class SlideContent {
  companion object {
    fun from(node: StructuralNode): SlideContents {
      if (node is org.asciidoctor.ast.List) {
        val textRanges = mutableListOf<TextRange>()
        var currentIndex = 0
        for (item in node.items) {
          val htmlText = (item as ListItem).text
          val body = Jsoup.parseBodyFragment(htmlText).body()
          textRanges.addAll(parseHtmlText(htmlText, body, currentIndex))
          // adds +1 because each line will be join with \n
          currentIndex += Parser.unescapeEntities(body.text(), true).length + 1
        }
        val type = if (node.context == "ulist" && node.isOption("checklist")) {
          "checklist"
        } else {
          node.context
        }
        val speakerNotes = if (type == "checklist" && node.hasRole("answers")) {
          var answers = "\nCorrect answer(s):\n"
          for (item in node.items) {
            if (item.hasAttribute("checked")) {
              val html = (item as ListItem).text
              val doc = Jsoup.parseBodyFragment(html)
              answers += "- ${doc.text()}\n"
            }
          }
          answers
        } else {
          ""
        }
        val rawText = node.items.joinToString("\n") {
          val text = (it as ListItem).text
          val doc = Jsoup.parseBodyFragment(text)
          Parser.unescapeEntities(doc.body().text(), true)
        }
        val listContent = ListContent(
          text = rawText,
          type = type,
          ranges = textRanges,
          roles = node.roles + node.parent.roles
        )
        return SlideContents(listOf(listContent), speakerNotes)
      }
      if (node.context == "org/asciidoctor/googleslides/image") {
        val url = node.document.getAttribute("imagesdir") as String + node.getAttribute("target") as String
        if (url.startsWith("http://") || url.startsWith("https://")) {
          val bufferedImage = ImageIO.read(URL(url))
          val height = bufferedImage.height
          val width = bufferedImage.width
          return SlideContents(listOf(ImageContent(url, height, width)))
        }
        throw IllegalArgumentException("Local images are not supported, the target must be a remote URL starting with http:// or https://")
      }
      if (node.context == "listing") {
        val rawText = node.content as String
        val code = Parser.unescapeEntities(rawText, true)
        val maxLineLength = code.split("\n").map { it.length }.max() ?: 0
        // remind: optimized for Roboto Mono
        val fontSize = when {
          maxLineLength > 121 -> 8
          maxLineLength > 109 -> 9
          maxLineLength > 99 -> 10
          maxLineLength > 91 -> 11
          maxLineLength > 84 -> 12
          maxLineLength > 78 -> 13
          else -> 14
        }
        return SlideContents(listOf(ListingContent(code.replace(Regex("\n"), "\u000b"), fontSize)))
      }
      if (node.context == "open") {
        val list = node.blocks.map { from(it) }
        if (list.isNotEmpty()) {
          return list.reduce { slide, acc ->
            SlideContents(slide.contents + acc.contents, slide.speakerNotes.orEmpty() + acc.speakerNotes.orEmpty())
          }
        }
        // FIXME: list empty???
        return SlideContents(listOf(TextContent("")))
      }
      // FIXME: null content???
      val htmlText = node.content as String? ?: ""
      val body = Jsoup.parseBodyFragment(htmlText).body()
      val textRanges = parseHtmlText(htmlText, body)
      val roles = node.roles + node.parent.roles
      val text = Parser.unescapeEntities(body.text(), true)
      return SlideContents(listOf(TextContent(
        text = text,
        ranges = textRanges,
        roles = roles
      )))
    }

    private fun parseHtmlText(htmlText: String, body: Element, initialIndex: Int = 0): List<TextRange> {
      val result = mutableListOf<TextRange>()
      val textWithoutHTML = body.text()
      if (textWithoutHTML == htmlText) {
        return result
      }
      // diff! add a text range to replace the inline HTML with a style
      var currentIndex = initialIndex
      for (htmlNode in body.childNodes()) {
        val textToken = when (htmlNode) {
          is TextNode -> {
            val text = Parser.unescapeEntities(htmlNode.text(), true)
            TextToken(text, "text")
          }
          is Element -> {
            val text = Parser.unescapeEntities(htmlNode.text(), true)
            TextToken(text, htmlNode.tagName())
          }
          else -> throw IllegalArgumentException("Unable to parse: $htmlNode")
        }
        val length = textToken.text.length
        result.add(TextRange(textToken, currentIndex, endIndex = currentIndex + length))
        currentIndex += length
      }
      return result
    }
  }
}

data class ListingContent(val text: String, val fontSize: Int) : SlideContent() {
  val ranges = listOf(TextRange(TextToken(text, "code"), 0, text.length))
}

data class ImageContent(val url: String, val height: Int, val width: Int, val padding: Double = 0.0, val offsetX: Double = 0.0, val offsetY: Double = 0.0) : SlideContent()
data class TextContent(val text: String, val ranges: List<TextRange> = emptyList(), val roles: List<String> = emptyList(), val fontSize: Int? = null) : SlideContent()
data class ListContent(val text: String, val type: String, val ranges: List<TextRange> = emptyList(), val roles: List<String> = emptyList()) : SlideContent()
data class TextRange(val token: TextToken, val startIndex: Int, val endIndex: Int)
data class TextToken(val text: String, val type: String)
data class SlideContents(val contents: List<SlideContent>, val speakerNotes: String? = null)

data class TitleAndTwoColumns(override val title: String?,
                              val rightColumn: SlideContents,
                              val leftColumn: SlideContents,
                              override val speakerNotes: String? = null) : Slide(title, speakerNotes)

data class TitleAndBodySlide(override val title: String?,
                             val body: SlideContents,
                             override val speakerNotes: String? = null) : Slide(title, speakerNotes)
