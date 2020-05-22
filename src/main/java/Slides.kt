import com.google.api.services.slides.v1.model.*
import java.util.*

fun main() {
    SlidesGenerator.run()
}

object SlidesGenerator {
    fun run() {
        val service = Client.service
        val presentations = service.presentations()
        var presentation = Presentation().setTitle("Introduction to Cypher")

        // create a presentation
        presentation = presentations.create(presentation)
                .setFields("layouts,masters,slides,presentationId")
                .execute()
        println("Created presentation with ID: ${presentation.presentationId}")

        // PROCESS
        // create a presentation
        // create slides with a layout
        // reload presentation
        // populate slides

        /*
          this.slides = extractSlides(markdown, css);
        this.allowUpload = useFileio;
        await this.generateImages();
        await this.probeImageSizes();
        await this.uploadLocalImages();
        await this.updatePresentation(this.createSlides());
        await this.reloadPresentation();
        await this.updatePresentation(this.populateSlides());
        return this.presentation.presentationId;
         */

        val layouts = presentation.layouts
        val slides = presentation.slides

        // Create slides
        val batchUpdatePresentationRequest = BatchUpdatePresentationRequest()
        var requests = mutableListOf<Request>()
        deleteExistingSlides(slides, requests)
        addCreateTitleSlide(layouts, requests)
        addCreateTitleAndBodySlide(layouts, requests)
        addCreateTwoColumnsSlide(layouts, requests)

        batchUpdatePresentationRequest.requests = requests
        presentations.batchUpdate(presentation.presentationId, batchUpdatePresentationRequest).execute()

        // Reload presentation
        presentation = presentations.get(presentation.presentationId).execute()

        // Populate slides
        requests = mutableListOf()
        val firstSlideTitle = presentation.slides[0].pageElements.first { it.shape.placeholder.type == "CENTERED_TITLE" }
        addInsertTextRequest(firstSlideTitle.objectId, "Introduction to Cypher", requests)

        val secondSlideTitle = presentation.slides[1].pageElements.first { it.shape.placeholder.type == "TITLE" }
        val secondSlideBody = presentation.slides[1].pageElements.first { it.shape.placeholder.type == "BODY" }
        addInsertTextRequest(secondSlideTitle.objectId, "Overview", requests)
        addInsertTextRequest(secondSlideBody.objectId, """At the end of this module, you should be able to write Cypher statements to:
Retrieve nodes from the graph.
Filter nodes retrieved using labels and property values of nodes.
Retrieve property values from nodes in the graph.
Filter nodes retrieved using relationships.
""", requests)

        val thirdSlide = presentation.slides[2]
        val thirdSlideTitle = thirdSlide.pageElements.first { it.shape.placeholder.type == "TITLE" }
        val bodies = thirdSlide.pageElements.filter { it.shape.placeholder.type == "BODY" }
        val thirdSlideLeftBody = bodies[0]
        val thirdSlideRightBody = bodies[1]
        addInsertTextRequest(thirdSlideTitle.objectId, "What is Cypher?", requests)
        val text = """Declarative query language
Focuses on what, not how to retrieve
Uses keywords such as MATCH, WHERE, CREATE
Runs in the database server for the graph
ASCII art to represent nodes and relationships
"""
        addInsertTextRequest(thirdSlideLeftBody.objectId, text, requests)
        addCreateParagraphBullets(thirdSlideLeftBody.objectId, text, requests)
        appendCreateImageRequests(presentation, thirdSlide, thirdSlideRightBody, requests)

        batchUpdatePresentationRequest.requests = requests
        presentations.batchUpdate(presentation.presentationId, batchUpdatePresentationRequest).execute()
    }

    private fun appendCreateImageRequests(presentation: Presentation, slide: Page, placeholder: PageElement, requests: MutableList<Request>) {
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
        createImageRequest.url = "https://raw.githubusercontent.com/neo4j-contrib/training-v3/58eef39b7199b58bd7d5679c348c0871a375b9f5/modules/demo-intro/images/Properties.png"
        request.createImage = createImageRequest
        requests.add(request)
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

private fun createNotePage(): Page {
    val notePage = Page()
    notePage.pageType = "NOTES"
    val pageProperties = PageProperties()
    val pageBackgroundFill = PageBackgroundFill()
    val solidFill = SolidFill()
    val color = OpaqueColor()
    val rgbColor = RgbColor()
    rgbColor.blue = 25f
    rgbColor.green = 55f
    rgbColor.red = 155f
    color.rgbColor = rgbColor
    solidFill.color = color
    pageBackgroundFill.solidFill = solidFill
    pageProperties.pageBackgroundFill = pageBackgroundFill
    notePage.pageProperties = pageProperties
    return notePage
}
