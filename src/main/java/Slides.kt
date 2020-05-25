import com.google.api.services.slides.v1.model.*
import org.asciidoctor.Asciidoctor.Factory.create
import org.asciidoctor.ast.ListItem
import org.asciidoctor.ast.Section
import org.asciidoctor.ast.StructuralNode
import java.util.*

fun main() {
  val asciidoctorPresentation = AsciidoctorSlides.generateFromAsciiDoc()
  println(asciidoctorPresentation)
  SlidesGenerator.run(asciidoctorPresentation)
}

object AsciidoctorSlides {

  fun generateFromAsciiDoc(): AsciidoctorPresentation {
    val asciidoctor = create()
    val document = asciidoctor.load(AsciidoctorSlides::class.java.getResource("/presentation.adoc").readText(), mapOf())
    val slides = document.blocks.mapNotNull { block ->
      if (block is Section) {
        val slideTitle = if (block.title == "!") null else block.title
        val slideBlocks = block.blocks
        val (speakerNotesBlocks, contentBlocks) = slideBlocks.partition { it.roles.contains("notes") }
        val speakerNotes = speakerNotesBlocks.joinToString("\n") { it.content as String }
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
        return ListContent(node.items.joinToString("\n") { (it as ListItem).text })
      }
      if (node.context == "image") {
        return ImageContent(node.document.getAttribute("imagesdir") as String + node.getAttribute("target") as String)
      }
      return TextContent(node.content as String)
    }
  }
}

data class ImageContent(val url: String) : SlideContent()
data class TextContent(val text: String) : SlideContent()
data class ListContent(val text: String) : SlideContent()

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
    addInsertTextRequest(firstSlideTitle.objectId, asciidoctorPresentation.title, requests)

    asciidoctorPresentation.slides.forEachIndexed { index, asciidoctorSlide ->
      val googleSlide = presentation.slides[index + 1]
      val speakerNotesObjectId = googleSlide.slideProperties.notesPage.notesProperties.speakerNotesObjectId
      if (speakerNotesObjectId != null && asciidoctorSlide.speakerNotes != null && asciidoctorSlide.speakerNotes!!.isNotBlank()) {
        addInsertTextRequest(speakerNotesObjectId, asciidoctorSlide.speakerNotes!!, requests)
      }
      if (asciidoctorSlide is AsciidoctorTitleAndBodySlide) {
        val slideTitle = googleSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val slideBody = googleSlide.pageElements.first { it.shape.placeholder.type == "BODY" }
        addInsertTextRequest(slideTitle.objectId, asciidoctorSlide.title.orEmpty(), requests)
        addContent(asciidoctorSlide.body.contents, presentation, googleSlide, slideBody, requests)
      } else if (asciidoctorSlide is AsciidoctorTitleAndTwoColumns) {
        val slideTitle = googleSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val bodies = googleSlide.pageElements.filter { it.shape.placeholder.type == "BODY" }
        val slideLeftBody = bodies[0]
        val slideRightBody = bodies[1]
        addInsertTextRequest(slideTitle.objectId, asciidoctorSlide.title.orEmpty(), requests)
        addContent(asciidoctorSlide.leftColumn.contents, presentation, googleSlide, slideLeftBody, requests)
        addContent(asciidoctorSlide.rightColumn.contents, presentation, googleSlide, slideRightBody, requests)
      }
    }
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
    for (content in contents) {
      when (content) {
        is ImageContent -> appendCreateImageRequests(content.url, presentation, googleSlide, placeholder, requests)
        is TextContent -> addInsertTextRequest(placeholder.objectId, content.text, requests)
        is ListContent -> {
          addInsertTextRequest(placeholder.objectId, content.text, requests)
          addCreateParagraphBullets(placeholder.objectId, content.text, requests)
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

  private fun addInsertTextRequest(placeholderObjectId: String, text: String, requests: MutableList<Request>) {
    val request = Request()
    val insertTextRequest = InsertTextRequest()
    insertTextRequest.text = text
    insertTextRequest.objectId = placeholderObjectId
    request.insertText = insertTextRequest
    requests.add(request)
  }

  private fun addCreateParagraphBullets(placeholderObjectId: String, text: String, requests: MutableList<Request>) {
    val request = Request()
    val createParagraphBulletsRequest = CreateParagraphBulletsRequest()
    val textRange = Range()
    textRange.type = "FIXED_RANGE"
    textRange.startIndex = 0
    textRange.endIndex = text.length
    createParagraphBulletsRequest.textRange = textRange
    createParagraphBulletsRequest.bulletPreset = "BULLET_DISC_CIRCLE_SQUARE"
    createParagraphBulletsRequest.objectId = placeholderObjectId
    request.createParagraphBullets = createParagraphBulletsRequest
    requests.add(request)
  }
}
