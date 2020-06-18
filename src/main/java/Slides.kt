import com.google.api.services.slides.v1.model.*
import layout.Item
import layout.Layout
import org.asciidoctor.ast.ListItem
import org.asciidoctor.ast.StructuralNode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.net.URL
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.min


fun main() {
  val asciidoctorPresentation = AsciidoctorPresentation.load()
  println(asciidoctorPresentation)
  SlidesGenerator.run(asciidoctorPresentation)
}

sealed class AsciidoctorSlide(open val title: String?, open val speakerNotes: String? = null)
sealed class SlideContent {
  companion object {
    fun map(node: StructuralNode): SlideContents {
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
      if (node.context == "image") {
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
        val list = node.blocks.map { map(it) }
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

data class AsciidoctorTitleAndTwoColumns(override val title: String?,
                                         val rightColumn: SlideContents,
                                         val leftColumn: SlideContents,
                                         override val speakerNotes: String? = null) : AsciidoctorSlide(title, speakerNotes)

data class AsciidoctorTitleAndBodySlide(override val title: String?,
                                        val body: SlideContents,
                                        override val speakerNotes: String? = null) : AsciidoctorSlide(title, speakerNotes)

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
    addInsertTextRequest(firstSlideTitle.objectId, TextContent(asciidoctorPresentation.title), 0, requests)

    asciidoctorPresentation.slides.forEachIndexed { index, asciidoctorSlide ->
      val googleSlide = presentation.slides[index + 1]
      val speakerNotesObjectId = googleSlide.slideProperties.notesPage.notesProperties.speakerNotesObjectId
      if (speakerNotesObjectId != null && asciidoctorSlide.speakerNotes != null && asciidoctorSlide.speakerNotes!!.isNotBlank()) {
        addInsertTextRequest(speakerNotesObjectId, TextContent(asciidoctorSlide.speakerNotes!!), 0, requests)
      }
      if (asciidoctorSlide is AsciidoctorTitleAndBodySlide) {
        val slideTitle = googleSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val slideBody = googleSlide.pageElements.first { it.shape.placeholder.type == "BODY" }
        addInsertTextRequest(slideTitle.objectId, TextContent(asciidoctorSlide.title.orEmpty()), 0, requests)
        addContent(asciidoctorSlide.body.contents, presentation, googleSlide, slideBody, requests)
      } else if (asciidoctorSlide is AsciidoctorTitleAndTwoColumns) {
        val slideTitle = googleSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val bodies = googleSlide.pageElements.filter { it.shape.placeholder.type == "BODY" }
        val slideLeftBody = bodies[0]
        val slideRightBody = bodies[1]
        addInsertTextRequest(slideTitle.objectId, TextContent(asciidoctorSlide.title.orEmpty()), 0, requests)
        addContent(asciidoctorSlide.leftColumn.contents, presentation, googleSlide, slideLeftBody, requests)
        addContent(asciidoctorSlide.rightColumn.contents, presentation, googleSlide, slideRightBody, requests)
      }
    }
    println(requests)
    batchUpdatePresentationRequest.requests = requests
    presentations.batchUpdate(presentation.presentationId, batchUpdatePresentationRequest).execute()
  }

  private fun appendCreateImagesRequests(images: List<ImageContent>, presentation: Presentation, slide: Page, placeholder: PageElement, requests: MutableList<Request>) {
    val packingSmithLayer = Layout.get(emptyMap())
    // default padding if more than one image on the slide
    val padding = if (images.size > 1) 10 else 0
    for (image in images) {
      packingSmithLayer.addItem(Item(image.height + (padding * 2), image.width + (padding * 2), meta = mapOf(
        "url" to image.url,
        "width" to image.width,
        "height" to image.height,
        "offsetX" to image.offsetX,
        "offsetY" to image.offsetY,
        "padding" to padding
      )))
    }
    val box = GenericLayout.getBodyBoundingBox(presentation, placeholder)
    val computedLayout = packingSmithLayer.export()
    val scaleRatio = min(box.width / computedLayout.width, box.height / computedLayout.height)
    val scaledWidth = computedLayout.width * scaleRatio
    val scaledHeight = computedLayout.height * scaleRatio
    val baseTranslateX = box.minX + (box.width - scaledWidth) / 2
    val baseTranslateY = box.minY + (box.height - scaledHeight) / 2
    for (item in computedLayout.items) {
      val itemOffsetX = item.meta.getOrDefault("offsetX", 0.0) as Number
      val itemOffsetY = item.meta.getOrDefault("offsetY", 0.0) as Number
      val itemPadding = item.meta.getOrDefault("padding", 0.0) as Number
      val width = item.meta["width"] as Int * scaleRatio
      val height = item.meta["height"] as Int * scaleRatio
      val translateX = baseTranslateX + (item.x + itemPadding.toDouble() + itemOffsetX.toDouble()) * scaleRatio
      val translateY = baseTranslateY + (item.y + itemPadding.toDouble() + itemOffsetY.toDouble()) * scaleRatio
      val request = Request()
      val createImageRequest = CreateImageRequest()
      val pageElementProperties = PageElementProperties()
      pageElementProperties.pageObjectId = slide.objectId
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
      transform.translateX = translateX
      transform.translateY = translateY
      transform.shearX = 0.0
      transform.shearY = 0.0
      transform.unit = "EMU"
      pageElementProperties.transform = transform
      createImageRequest.elementProperties = pageElementProperties
      createImageRequest.url = item.meta["url"] as String
      request.createImage = createImageRequest
      requests.add(request)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun addContent(contents: List<SlideContent>, presentation: Presentation, googleSlide: Page, placeholder: PageElement, requests: MutableList<Request>) {
    var currentIndex = 0
    val contentPerType = contents.groupBy { it.javaClass.simpleName }
    val images = contentPerType[ImageContent::class.simpleName] as List<ImageContent>?
    if (images != null && images.isNotEmpty()) {
      appendCreateImagesRequests(images, presentation, googleSlide, placeholder, requests)
    }
    contents.filterNot { it is ImageContent }.forEachIndexed { index, content ->
      when (content) {
        is TextContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholder.objectId, TextContent(text, content.ranges, content.roles, content.fontSize), currentIndex, requests)
          currentIndex += text.length
        }
        is ListContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholder.objectId, TextContent(text, content.ranges, content.roles), currentIndex, requests)
          addCreateParagraphBullets(placeholder.objectId, ListContent(text, content.type, content.ranges, content.roles), currentIndex, requests)
          currentIndex += text.length
        }
        is ListingContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholder.objectId, TextContent(text, ranges = content.ranges, fontSize = content.fontSize), currentIndex, requests)
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

  private fun addInsertTextRequest(placeholderObjectId: String, textContent: TextContent, insertionIndex: Int = 0, requests: MutableList<Request>) {
    val text = textContent.text
    val textRanges = textContent.ranges
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
        val bgColor = OptionalColor()
        val bgOpaqueColor = OpaqueColor()
        bgOpaqueColor.themeColor = "LIGHT1"
        bgColor.opaqueColor = bgOpaqueColor
        textStyle.backgroundColor = bgColor
        textStyle.fontFamily = "Roboto Mono"
        textStyle.bold = true
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
    val fontSize = textContent.fontSize
      ?: if (textContent.roles.contains("small") || textContent.roles.contains("statement")) {
        13
      } else {
        null
      }
    if (fontSize != null) {
      val updateTextStyleRequest = UpdateTextStyleRequest()
      val range = Range()
      range.type = "FIXED_RANGE"
      range.startIndex = insertionIndex
      range.endIndex = insertionIndex + text.length
      updateTextStyleRequest.textRange = range
      val textStyle = TextStyle()
      val fontSizeDimension = Dimension()
      fontSizeDimension.magnitude = fontSize.toDouble()
      fontSizeDimension.unit = "PT"
      textStyle.fontSize = fontSizeDimension
      updateTextStyleRequest.style = textStyle
      updateTextStyleRequest.objectId = placeholderObjectId
      updateTextStyleRequest.fields = "fontSize"
      val textRangeRequest = Request()
      textRangeRequest.updateTextStyle = updateTextStyleRequest
      requests.add(textRangeRequest)
    }
  }

  private fun addCreateParagraphBullets(placeholderObjectId: String, listContent: ListContent, insertionIndex: Int = 0, requests: MutableList<Request>) {
    val request = Request()
    val createParagraphBulletsRequest = CreateParagraphBulletsRequest()
    val textRange = Range()
    textRange.type = "FIXED_RANGE"
    textRange.startIndex = insertionIndex
    textRange.endIndex = insertionIndex + listContent.text.length
    createParagraphBulletsRequest.textRange = textRange
    createParagraphBulletsRequest.bulletPreset = when (listContent.type) {
      "checklist" -> "BULLET_CHECKBOX"
      "olist" -> "NUMBERED_DIGIT_ALPHA_ROMAN"
      else -> "BULLET_DISC_CIRCLE_SQUARE"
    }
    createParagraphBulletsRequest.objectId = placeholderObjectId
    request.createParagraphBullets = createParagraphBulletsRequest
    requests.add(request)
  }
}
