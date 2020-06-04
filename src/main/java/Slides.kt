import com.google.api.services.slides.v1.model.*
import org.asciidoctor.Asciidoctor.Factory.create
import org.asciidoctor.ast.ListItem
import org.asciidoctor.ast.Section
import org.asciidoctor.ast.StructuralNode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.util.*


fun main() {
  val asciidoctorPresentation = AsciidoctorSlides.generateFromAsciiDoc()
  println(asciidoctorPresentation)
  SlidesGenerator.run(asciidoctorPresentation)
}

object AsciidoctorSlides {

  fun generateFromAsciiDoc(): AsciidoctorPresentation {
    val asciidoctor = create()
    val document = asciidoctor.load(AsciidoctorSlides::class.java.getResource("/4.0-implementing-graph-data-models.adoc").readText(), mapOf())
    val slides = document.blocks.mapNotNull { block ->
      if (block is Section) {
        val slideTitle = if (block.title == "!") null else block.title
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
            AsciidoctorTitleAndTwoColumns(slideTitle, SlideContents(listOf(SlideContent.map(rightBlock))), SlideContents(listOf(SlideContent.map(leftBlock))), speakerNotes)
          } else {
            println("WARNING: a two-columns block must have exactly 2 nested blocks, ignoring!")
            null
          }
        } else {
          val contents = SlideContents(contentBlocks.map { SlideContent.map(it) })
          AsciidoctorTitleAndBodySlide(slideTitle, contents, speakerNotes)
        }
      } else {
        null
      }
    }
    return AsciidoctorPresentation(document.doctitle, slides)
  }
}

sealed class AsciidoctorSlide(open val title: String?, open val speakerNotes: String? = null)
sealed class SlideContent {
  companion object {
    fun map(node: StructuralNode): SlideContent {
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
        return ListContent(node.items.joinToString("\n") {
          val text = (it as ListItem).text
          val doc = Jsoup.parseBodyFragment(text)
          Parser.unescapeEntities(doc.body().text(), true)
        }, textRanges)
      }
      if (node.context == "image") {
        return ImageContent(node.document.getAttribute("imagesdir") as String + node.getAttribute("target") as String)
      }
      if (node.context == "listing") {
        val htmlText = node.content as String
        val body = Jsoup.parseBodyFragment(htmlText).body()
        return ListingContent(Parser.unescapeEntities(body.text(), true))
      }
      val htmlText = node.content as String
      val body = Jsoup.parseBodyFragment(htmlText).body()
      val textRanges = parseHtmlText(htmlText, body)
      return TextContent(Parser.unescapeEntities(body.text(), true), textRanges)
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

data class ListingContent(val text: String) : SlideContent() {
  val ranges = listOf(TextRange(TextToken(text, "code"), 0, text.length))
}
data class ImageContent(val url: String) : SlideContent()
data class TextContent(val text: String, val ranges: List<TextRange> = emptyList()) : SlideContent()
data class ListContent(val text: String, val ranges: List<TextRange> = emptyList()) : SlideContent()
data class TextRange(val token: TextToken, val startIndex: Int, val endIndex: Int)
data class TextToken(val text: String, val type: String)
data class SlideContents(val contents: List<SlideContent>)

data class AsciidoctorTitleAndTwoColumns(override val title: String?,
                                         val rightColumn: SlideContents,
                                         val leftColumn: SlideContents,
                                         override val speakerNotes: String? = null)
  : AsciidoctorSlide(title, speakerNotes)

data class AsciidoctorTitleAndBodySlide(override val title: String?,
                                        val body: SlideContents,
                                        override val speakerNotes: String? = null)
  : AsciidoctorSlide(title, speakerNotes)

data class AsciidoctorPresentation(val title: String, val slides: List<AsciidoctorSlide>)

object SlidesGenerator {
  fun run(asciidoctorPresentation: AsciidoctorPresentation) {
    val service = Client.service
    val presentations = service.presentations()
    var presentation = Presentation().setTitle(asciidoctorPresentation.title)

    // create a presentation
    presentation = presentations.create(presentation)
      .setFields("layouts,masters,slides,presentationId")
      .execute()
    println("Created presentation with ID: ${presentation.presentationId}")

    val layouts = presentation.layouts
    val slides = presentation.slides

    // Create slides
    val batchUpdatePresentationRequest = BatchUpdatePresentationRequest()
    var requests = mutableListOf<Request>()
    deleteExistingSlides(slides, requests)

    addCreateTitleSlide(layouts, requests)
    for (slide in asciidoctorPresentation.slides) {
      if (slide is AsciidoctorTitleAndBodySlide) {
        addCreateTitleAndBodySlide(layouts, requests)
      } else if (slide is AsciidoctorTitleAndTwoColumns) {
        addCreateTwoColumnsSlide(layouts, requests)
      }
    }
    batchUpdatePresentationRequest.requests = requests
    presentations.batchUpdate(presentation.presentationId, batchUpdatePresentationRequest).execute()

    // Reload presentation
    presentation = presentations.get(presentation.presentationId).execute()

    // Populate slides
    requests = mutableListOf()

    val firstSlideTitle = presentation.slides[0].pageElements.first { it.shape.placeholder.type == "CENTERED_TITLE" }
    addInsertTextRequest(firstSlideTitle.objectId, asciidoctorPresentation.title, 0, emptyList(), requests)

    asciidoctorPresentation.slides.forEachIndexed { index, asciidoctorSlide ->
      val googleSlide = presentation.slides[index + 1]
      val speakerNotesObjectId = googleSlide.slideProperties.notesPage.notesProperties.speakerNotesObjectId
      if (speakerNotesObjectId != null && asciidoctorSlide.speakerNotes != null && asciidoctorSlide.speakerNotes!!.isNotBlank()) {
        addInsertTextRequest(speakerNotesObjectId, asciidoctorSlide.speakerNotes!!, 0, emptyList(), requests)
      }
      if (asciidoctorSlide is AsciidoctorTitleAndBodySlide) {
        val slideTitle = googleSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val slideBody = googleSlide.pageElements.first { it.shape.placeholder.type == "BODY" }
        addInsertTextRequest(slideTitle.objectId, asciidoctorSlide.title.orEmpty(), 0, emptyList(), requests)
        addContent(asciidoctorSlide.body.contents, presentation, googleSlide, slideBody, requests)
      } else if (asciidoctorSlide is AsciidoctorTitleAndTwoColumns) {
        val slideTitle = googleSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val bodies = googleSlide.pageElements.filter { it.shape.placeholder.type == "BODY" }
        val slideLeftBody = bodies[0]
        val slideRightBody = bodies[1]
        addInsertTextRequest(slideTitle.objectId, asciidoctorSlide.title.orEmpty(), 0, emptyList(), requests)
        addContent(asciidoctorSlide.leftColumn.contents, presentation, googleSlide, slideLeftBody, requests)
        addContent(asciidoctorSlide.rightColumn.contents, presentation, googleSlide, slideRightBody, requests)
      }
    }
    println(requests)
    batchUpdatePresentationRequest.requests = requests
    presentations.batchUpdate(presentation.presentationId, batchUpdatePresentationRequest).execute()
  }

  private fun appendCreateImageRequests(url: String, presentation: Presentation, slide: Page, placeholder: PageElement, requests: MutableList<Request>) {
    val box = GenericLayout.getBodyBoundingBox(presentation, placeholder)
    val request = Request()
    val createImageRequest = CreateImageRequest()
    val pageElementProperties = PageElementProperties()
    pageElementProperties.pageObjectId = slide.objectId
    val scaleRatio = 1.0
    val width = box.width * scaleRatio
    val height = box.height * scaleRatio
    val size = Size()
    val heightDimension = Dimension()
    heightDimension.magnitude = height
    heightDimension.unit = "EMU"
    size.height = heightDimension
    val widthDimension = Dimension()
    widthDimension.magnitude = width
    widthDimension.unit = "EMU"
    size.width = widthDimension
    pageElementProperties.setSize(size)
    val transform = AffineTransform()
    transform.scaleX = 1.0
    transform.scaleY = 1.0
    transform.translateX = box.minX
    transform.translateY = box.minY
    transform.unit = "EMU"
    pageElementProperties.transform = transform
    createImageRequest.elementProperties = pageElementProperties
    createImageRequest.url = url
    request.createImage = createImageRequest
    requests.add(request)
  }

  private fun addContent(contents: List<SlideContent>, presentation: Presentation, googleSlide: Page, placeholder: PageElement, requests: MutableList<Request>) {
    var currentIndex = 0
    contents.forEachIndexed { index, content ->
      when (content) {
        is ImageContent -> appendCreateImageRequests(content.url, presentation, googleSlide, placeholder, requests)
        is TextContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholder.objectId, text, currentIndex, content.ranges, requests)
          currentIndex += text.length
        }
        is ListContent -> {
          addInsertTextRequest(placeholder.objectId, content.text, currentIndex, content.ranges, requests)
          addCreateParagraphBullets(placeholder.objectId, content.text, currentIndex, requests)
        }
        is ListingContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholder.objectId, text, currentIndex, content.ranges, requests)
          currentIndex += text.length
        }
      }
    }
  }

  private fun deleteExistingSlides(slides: List<Page>, requests: MutableList<Request>) {
    requests.addAll(slides.map {
      val request = Request()
      val deleteObjectRequest = DeleteObjectRequest()
      deleteObjectRequest.objectId = slides.first().objectId
      request.deleteObject = deleteObjectRequest
      request
    })
  }

  private fun addCreateTwoColumnsSlide(layouts: List<Page>, requests: MutableList<Request>) {
    val titleLayout = layouts.first { it.layoutProperties.name == "TITLE_AND_TWO_COLUMNS" }
    addCreateSlideRequest(titleLayout, UUID.randomUUID().toString(), requests)
  }

  private fun addCreateTitleSlide(layouts: List<Page>, requests: MutableList<Request>) {
    val titleLayout = layouts.first { it.layoutProperties.name == "TITLE" }
    addCreateSlideRequest(titleLayout, UUID.randomUUID().toString(), requests)
  }

  private fun addCreateTitleAndBodySlide(layouts: List<Page>, requests: MutableList<Request>) {
    val titleAndBodyLayout = layouts.first { it.layoutProperties.name == "TITLE_AND_BODY" }
    addCreateSlideRequest(titleAndBodyLayout, UUID.randomUUID().toString(), requests)
  }

  private fun addCreateSlideRequest(layout: Page, objectId: String, requests: MutableList<Request>) {
    val request = Request()
    val createSlide = CreateSlideRequest()
    val slideLayoutReference = LayoutReference()
    slideLayoutReference.layoutId = layout.objectId
    createSlide.slideLayoutReference = slideLayoutReference
    createSlide.objectId = objectId
    request.createSlide = createSlide
    requests.add(request)
  }

  private fun addInsertTextRequest(placeholderObjectId: String, text: String, insertionIndex: Int = 0, textRanges: List<TextRange>, requests: MutableList<Request>) {
    val request = Request()
    val insertTextRequest = InsertTextRequest()
    insertTextRequest.text = text
    insertTextRequest.objectId = placeholderObjectId
    insertTextRequest.insertionIndex = insertionIndex
    request.insertText = insertTextRequest
    requests.add(request)
    for (textRange in textRanges) {
      val textStyle = TextStyle()
      val type = textRange.token.type
      if (type == "code") {
        val fgColor = OptionalColor()
        val fgOpaqueColor = OpaqueColor()
        fgOpaqueColor.themeColor = "ACCENT1"
        fgColor.opaqueColor = fgOpaqueColor
        textStyle.foregroundColor = fgColor
        val bgColor = OptionalColor()
        val bgOpaqueColor = OpaqueColor()
        bgOpaqueColor.themeColor = "LIGHT1"
        bgColor.opaqueColor = bgOpaqueColor
        textStyle.backgroundColor = bgColor
        textStyle.fontFamily = "Roboto Mono"
      } else if (type == "em") {
        textStyle.italic = true
      } else if (type == "strong" || type == "b") {
        textStyle.bold = true
      } else {
        // ignore
        println("Unsupported token type: ${textRange.token.type}")
        continue
      }
      val textRangeRequest = Request()
      val range = Range()
      range.type = "FIXED_RANGE"
      range.startIndex = insertionIndex + textRange.startIndex
      range.endIndex = insertionIndex + textRange.endIndex
      val updateTextStyleRequest = UpdateTextStyleRequest()
      updateTextStyleRequest.textRange = range
      updateTextStyleRequest.style = textStyle
      updateTextStyleRequest.objectId = placeholderObjectId
      updateTextStyleRequest.fields = "*"
      textRangeRequest.updateTextStyle = updateTextStyleRequest
      requests.add(textRangeRequest)
    }
  }

  private fun addCreateParagraphBullets(placeholderObjectId: String, text: String, startIndex: Int = 0, requests: MutableList<Request>) {
    val request = Request()
    val createParagraphBulletsRequest = CreateParagraphBulletsRequest()
    val textRange = Range()
    textRange.type = "FIXED_RANGE"
    textRange.startIndex = startIndex
    textRange.endIndex = startIndex + text.length
    createParagraphBulletsRequest.textRange = textRange
    createParagraphBulletsRequest.bulletPreset = "BULLET_DISC_CIRCLE_SQUARE"
    createParagraphBulletsRequest.objectId = placeholderObjectId
    request.createParagraphBullets = createParagraphBulletsRequest
    requests.add(request)
  }
}
