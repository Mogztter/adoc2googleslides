import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.slides.v1.Slides
import com.google.api.services.slides.v1.SlidesScopes
import com.google.api.services.slides.v1.model.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

fun main() {
    SlidesGenerator.run()
}

object SlidesGenerator {
    private const val APPLICATION_NAME = "Google Slides API Java Quickstart"
    private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
    private const val TOKENS_DIRECTORY_PATH = "tokens"

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private val SCOPES = listOf(SlidesScopes.PRESENTATIONS)
    private const val CREDENTIALS_FILE_PATH = "/credentials.json"

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    @Throws(IOException::class)
    private fun getCredentials(HTTP_TRANSPORT: NetHttpTransport): Credential {
        // Load client secrets.
        val inputStream = SlidesGenerator::class.java.getResourceAsStream(CREDENTIALS_FILE_PATH)
                ?: throw FileNotFoundException("Resource not found: $CREDENTIALS_FILE_PATH")
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(inputStream))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    fun run() {
        // Build a new authorized API client service.
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val service = Slides.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build()
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
