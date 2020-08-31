package org.asciidoctor.googleslides

import org.asciidoctor.ast.*
import org.asciidoctor.jruby.internal.RubyObjectWrapper
import org.jruby.java.proxies.ConcreteJavaProxy
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URL
import javax.imageio.ImageIO
import org.asciidoctor.ast.List as AsciidoctorList


data class SlideDeck(val title: String, val slides: List<Slide>) {

  companion object {
    private val logger = LoggerFactory.getLogger(SlideDeck::class.java)

    fun from(document: Document): SlideDeck {
      flattenDocument(document)
      val slides = document.blocks.mapNotNull { block ->
        if (block is Section) {
          fromSection(block)
        } else {
          null
        }
      }
      return SlideDeck(document.doctitle, slides)
    }

    fun fromSection(block: Section): Slide? {
      val slideTitle = if (block.title == "!") {
        null
      } else {
        // FIXME: extract text transformation + range
        val doc = Jsoup.parseBodyFragment(block.title)
        doc.text()
      }
      val slideBlocks = block.blocks
      val (speakerNotesBlocks, contentBlocks) = slideBlocks.partition { it.roles.contains("notes") }
      val speakerNotesContents = speakerNotesBlocks.flatMap { structuralNode ->
        if (structuralNode.blocks.isNotEmpty()) {
          structuralNode.blocks.map { structuralChildNode ->
            val structuralChildNodeRubyObject = (structuralChildNode as RubyObjectWrapper).rubyObject
            (structuralChildNodeRubyObject.callMethod(structuralChildNodeRubyObject.runtime.currentContext, "convert") as ConcreteJavaProxy).toJava(SlideContents::class.java)
          }
        } else {
          val structuralNodeRubyObject = (structuralNode as RubyObjectWrapper).rubyObject
          listOf((structuralNodeRubyObject.callMethod(structuralNodeRubyObject.runtime.currentContext, "convert") as ConcreteJavaProxy).toJava(SlideContents::class.java))
        }
      }
      return if (contentBlocks.size == 1 && contentBlocks.first().roles.contains("two-columns")) {
        val twoColumnsBlock = contentBlocks.first()
        if (twoColumnsBlock.blocks.size == 2) {
          val leftBlock = twoColumnsBlock.blocks[0]
          val rightBlock = twoColumnsBlock.blocks[1]
          val rightBlockRuby = (rightBlock as RubyObjectWrapper).rubyObject
          val leftBlockRuby = (leftBlock as RubyObjectWrapper).rubyObject
          val rightColumnContents = (rightBlockRuby.callMethod(rightBlockRuby.runtime.currentContext, "convert") as ConcreteJavaProxy).toJava(SlideContents::class.java)
          val leftColumnContents = (leftBlockRuby.callMethod(leftBlockRuby.runtime.currentContext, "convert") as ConcreteJavaProxy).toJava(SlideContents::class.java)
          TitleAndTwoColumns(
            title = slideTitle,
            rightColumn = rightColumnContents,
            leftColumn = leftColumnContents,
            speakerNotes = speakerNotesContents + rightColumnContents.speakerNotes + leftColumnContents.speakerNotes,
            layoutId = resolveLayoutFromRoles(block, "TITLE_AND_TWO_COLUMNS")
          )
        } else {
          logger.warn("A two-columns block must have exactly 2 nested blocks, ignoring.")
          null
        }
      } else {
        val slideContents = contentBlocks.map { structuralNode ->
          val structuralNodeRubyObject = (structuralNode as RubyObjectWrapper).rubyObject
          (structuralNodeRubyObject.callMethod(structuralNodeRubyObject.runtime.currentContext, "convert") as ConcreteJavaProxy).toJava(SlideContents::class.java)
        }
        val contents = if (slideContents.isNotEmpty()) {
          slideContents.reduce { slide, acc ->
            SlideContents(slide.contents + acc.contents, slide.speakerNotes + acc.speakerNotes)
          }
        } else {
          SlideContents(emptyList())
        }
        if (contents.contents.isEmpty()) {
          TitleOnlySlide(
            title = slideTitle,
            speakerNotes = speakerNotesContents + contents.speakerNotes,
            layoutId = resolveLayoutFromRoles(block, "TITLE_ONLY")
          )
        } else {
          TitleAndBodySlide(
            title = slideTitle,
            body = contents,
            speakerNotes = speakerNotesContents + contents.speakerNotes,
            layoutId = resolveLayoutFromRoles(block, "TITLE_AND_BODY")
          )
        }
      }
    }

    private fun resolveLayoutFromRoles(block: ContentNode, defaultValue: String): String {
      val roles = block.roles
      for (role in roles) {
        if (block.document.hasAttribute("google-slides-layout-$role")) {
          return block.document.getAttribute("google-slides-layout-$role") as String
        }
      }
      return defaultValue
    }

    private fun flattenDocument(document: Document) {
      document.findBy(mapOf("context" to ":section")).filter {
        it.level > 1
      }.reversed().forEach { section ->
        var parentSection = (section.parent as Section)
        parentSection.blocks.remove(section)
        while (parentSection.parent != null && parentSection.parent.context == "section") {
          parentSection = parentSection.parent as Section
        }
        val rubyBlock = section as RubyObjectWrapper
        val ruby = rubyBlock.rubyObject.runtime
        rubyBlock.setRubyProperty("@level", ruby.newFixnum(1))
        document.blocks.add(parentSection.index + 1, section)
      }
    }
  }
}

sealed class SlideContent {
  companion object {

    private val logger = LoggerFactory.getLogger(SlideContent::class.java)

    fun from(node: StructuralNode): SlideContents {
      if (node is AsciidoctorList) {
        val textRanges = mutableListOf<TextRange>()
        var currentIndex = 0
        for (htmlText in extractHtmlTextFromList(node))  {
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
        val speakerNotesContents = listOf(SlideContents(listOf(TextContent(speakerNotes))))
        val textItems = extractRawTextFromList(node)
        val listContent = ListContent(
          text = textItems.joinToString("\n"),
          type = type,
          ranges = textRanges,
          roles = node.roles + node.parent.roles
        )
        return SlideContents(listOf(listContent), speakerNotesContents)
      }
      if (node.context == "image") {
        val imagesDirectory = node.document.getAttribute("imagesdir") as String?
        val target = node.getAttribute("target") as String
        val url = if (target.startsWith("http://") || target.startsWith("https://")) {
          target
        } else if (imagesDirectory != null) {
          imagesDirectory + target
        } else {
          target
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
          try {
            val bufferedImage = ImageIO.read(URL(url))
            val height = bufferedImage.height
            val width = bufferedImage.width
            return SlideContents(listOf(ImageContent(url, height, width)))
          } catch (e: IOException) {
            logger.error("Unable to read image: $url", e)
            throw e
          }
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
            SlideContents(slide.contents + acc.contents, slide.speakerNotes + acc.speakerNotes)
          }
        }
        logger.warn("Open block is empty, ignoring.")
        return SlideContents(listOf(TextContent("")))
      }
      if (node.context == "admonition") {
        return if (node.contentModel == "simple") {
          SlideContents(listOf(TextContent(
            text = "${node.style}: ${node.content as String}",
            roles = node.roles + node.parent.roles
          )))
        } else {
          logger.warn("Complex admonitions are not supported, ignoring.")
          SlideContents(listOf())
        }
      }
      if (node is Table) {
        val headerRows = node.header.map { fromAsciidoctorRow(it, "header") }
        val bodyRows = node.body.map { fromAsciidoctorRow(it, "body") }
        val footerRows = node.footer.map { fromAsciidoctorRow(it, "footer") }
        return SlideContents(listOf(TableContent(
          rows = headerRows + bodyRows + footerRows,
          columns = node.columns.size,
          roles = node.roles + node.parent.roles
        )))
      }
      val content = node.content
      return if (content is String) {
        val body = Jsoup.parseBodyFragment(content).body()
        val textRanges = parseHtmlText(content, body)
        val text = Parser.unescapeEntities(body.text(), true)
        SlideContents(listOf(TextContent(
          text = text,
          ranges = textRanges,
          roles = node.roles + node.parent.roles
        )))
      } else {
        logger.warn("Unable to retrieve the content for ${node.context}, ignoring.")
        SlideContents(listOf())
      }
    }

    private fun extractHtmlTextFromList(list: AsciidoctorList, depth: Int = 0): List<String> {
      return list.items.flatMap { listItem ->
        val text = (listItem as ListItem).text
        // adds a zero-width joiner to emulate a \t
        listOf("\u200D".repeat(depth) + text) + listItem.blocks.filterIsInstance<AsciidoctorList>().flatMap {
          extractHtmlTextFromList(it, depth + 1)
        }
      }
    }

    private fun extractRawTextFromList(list: AsciidoctorList, depth: Int = 0): List<String> {
      return list.items.flatMap { listItem ->
        val text = (listItem as ListItem).text
        val doc = Jsoup.parseBodyFragment(text)
        listOf("\t".repeat(depth) + Parser.unescapeEntities(doc.body().text(), true)) + listItem.blocks.filterIsInstance<AsciidoctorList>().flatMap {
          extractRawTextFromList(it, depth + 1)
        }
      }
    }

    private fun fromAsciidoctorRow(row: Row, style: String): TableRow {
      val cells = row.cells.map { cell ->
        TableCell(cell.text, style)
      }
      return TableRow(cells)
    }

    private fun parseHtmlText(htmlText: String, body: Element, initialIndex: Int = 0): List<TextRange> {
      val result = mutableListOf<TextRange>()
      // diff! add a text range to replace the inline HTML with a style
      var currentIndex = initialIndex
      val textWithoutHTML = body.text()
      if (textWithoutHTML == htmlText) {
        return result
      }
      val inlineTokens = mutableListOf<InlineToken>()
      for (htmlNode in body.childNodes()) {
        parseTextToken(htmlNode = htmlNode, agg = inlineTokens)
      }
      inlineTokens.map { inlineToken ->
        val length = inlineToken.text.length
        result.add(TextRange(inlineToken, currentIndex, endIndex = currentIndex + length))
        currentIndex += length
      }
      return result
    }

    private fun parseTextToken(htmlNode: Node, roles: List<String> = emptyList(), agg: MutableList<InlineToken> = mutableListOf()) {
      when (htmlNode) {
        is TextNode -> {
          val text = Parser.unescapeEntities(htmlNode.text(), true)
          agg.add(TextToken(text, roles))
        }
        is Element -> {
          val text = Parser.unescapeEntities(htmlNode.text(), true)
          when {
            htmlNode.tagName() == "a" -> agg.add(AnchorToken(text, htmlNode.attr("href"), roles))
            htmlNode.tagName() == "span" -> {
              val childNodes = htmlNode.childNodes()
              if (childNodes.isNotEmpty()) {
                for (childNode in childNodes) {
                  parseTextToken(childNode, roles + htmlNode.classNames().toList(), agg)
                }
              } else {
                agg.add(TextToken(text, roles))
              }
            }
            else -> {
              val childNodes = htmlNode.childNodes()
              if (childNodes.isNotEmpty()) {
                for (childNode in childNodes) {
                  parseTextToken(childNode, roles + listOf(htmlNode.tagName()), agg)
                }
              } else {
                agg.add(TextToken(text, roles + listOf(htmlNode.tagName())))
              }
            }
          }
        }
        else -> throw IllegalArgumentException("Unable to parse: $htmlNode")
      }
    }
  }
}

data class ListingContent(val text: String, val fontSize: Int) : SlideContent() {
  val ranges = listOf(TextRange(TextToken(text, roles = listOf("code")), 0, text.length))
}

sealed class InlineToken(open val text: String, open val roles: List<String>)
data class TextToken(override val text: String, override val roles: List<String> = listOf()) : InlineToken(text, roles)
data class AnchorToken(override val text: String, val target: String, override val roles: List<String> = listOf()) : InlineToken(text, roles)

data class ImageContent(val url: String, val height: Int, val width: Int, val padding: Double = 0.0, val offsetX: Double = 0.0, val offsetY: Double = 0.0) : SlideContent()
data class TextContent(val text: String, val ranges: List<TextRange> = emptyList(), val roles: List<String> = emptyList(), val fontSize: Int? = null) : SlideContent()
data class ListContent(val text: String, val type: String, val ranges: List<TextRange> = emptyList(), val roles: List<String> = emptyList()) : SlideContent()
data class TextRange(val token: InlineToken, val startIndex: Int, val endIndex: Int)
data class SlideContents(val contents: List<SlideContent>, val speakerNotes: List<SlideContents> = emptyList())
data class TableCell(val text: String, val style: String)
data class TableRow(val cells: List<TableCell>)
data class TableContent(val rows: List<TableRow>, val columns: Int, val roles: List<String> = emptyList()) : SlideContent()

sealed class Slide(open val title: String?, open val speakerNotes: List<SlideContents> = emptyList(), open val layoutId: String)
data class TitleAndTwoColumns(override val title: String?,
                              val rightColumn: SlideContents,
                              val leftColumn: SlideContents,
                              override val speakerNotes: List<SlideContents> = emptyList(),
                              override val layoutId: String)
  : Slide(title, speakerNotes, "TITLE_AND_TWO_COLUMNS")

data class TitleAndBodySlide(override val title: String?,
                             val body: SlideContents,
                             override val speakerNotes: List<SlideContents> = emptyList(),
                             override val layoutId: String)
  : Slide(title, speakerNotes, layoutId)

data class TitleOnlySlide(override val title: String?,
                          override val speakerNotes: List<SlideContents> = emptyList(),
                          override val layoutId: String)
  : Slide(title, speakerNotes, layoutId)
