package org.asciidoctor.googleslides

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.slides.v1.Slides
import com.google.api.services.slides.v1.SlidesScopes
import com.google.api.services.slides.v1.model.*
import org.asciidoctor.googleslides.layout.GenericLayout
import org.asciidoctor.googleslides.layout.Item
import org.asciidoctor.googleslides.layout.Layout
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.min
import com.google.api.services.drive.model.File as DriveFile


object GoogleSlidesGenerator {
  private val logger = LoggerFactory.getLogger(GoogleSlidesGenerator::class.java)

  fun generate(slideDeck: SlideDeck, slidesService: Slides, driveService: Drive, presentationId: String?, copyId: String?): String {
    val googleSlidesPresentation = initialize(slideDeck, slidesService, driveService, presentationId, copyId)

    val requests = mutableListOf<Request>()

    val firstSlideTitle = googleSlidesPresentation.slides[0].pageElements.first { it.shape.placeholder.type == "CENTERED_TITLE" }
    addInsertTextRequest(firstSlideTitle.objectId, TextContent(googleSlidesPresentation.title), 0, requests)

    slideDeck.slides.forEachIndexed { index, slide ->
      val googleSlide = googleSlidesPresentation.slides[index + 1]
      val speakerNotesObjectId = googleSlide.slideProperties.notesPage.notesProperties.speakerNotesObjectId
      if (speakerNotesObjectId != null && slide.speakerNotes.isNotEmpty()) {
        addTextualContent(slide.speakerNotes.flatMap { it.contents }, speakerNotesObjectId, requests)
      }
      // QUESTION: should we use a "features"/"capabilities" detection mechanism for the layout (i.e. has a title, has a body, has two columns...)
      val pageElements = googleSlide.pageElements
      if (pageElements == null || pageElements.isEmpty()) {
        logger.warn("No layout found for slide: $slide, unable to insert content, skipping")
      } else {
        when (slide) {
          is TitleOnlySlide -> {
            addTitle(pageElements, slide, requests)
          }
          is TitleAndBodySlide -> {
            addTitle(pageElements, slide, requests)
            val slideBody = pageElements.find { it.shape.placeholder.type == "BODY" }
            if (slideBody == null) {
              logger.warn("No BODY found on the slide elements: $pageElements, body won't be added")
            } else {
              addContent(slide.body.contents, googleSlidesPresentation, googleSlide, slideBody, requests)
            }
          }
          is TitleAndTwoColumns -> {
            addTitle(pageElements, slide, requests)
            val bodies = pageElements.filter { it.shape.placeholder.type == "BODY" }
            if (bodies.size < 2) {
              logger.warn("Unable to found at least 2 BODY elements on the slide elements: $pageElements, body won't be added")
            } else {
              val slideLeftBody = bodies[0]
              val slideRightBody = bodies[1]
              addContent(slide.leftColumn.contents, googleSlidesPresentation, googleSlide, slideLeftBody, requests)
              addContent(slide.rightColumn.contents, googleSlidesPresentation, googleSlide, slideRightBody, requests)
            }
          }
        }
      }
    }
    logger.debug("batchUpdatePresentationRequest.requests: $requests")
    val batchUpdatePresentationRequest = BatchUpdatePresentationRequest()
    batchUpdatePresentationRequest.requests = requests
    slidesService.presentations().batchUpdate(googleSlidesPresentation.presentationId, batchUpdatePresentationRequest).execute()
    return googleSlidesPresentation.presentationId
  }

  private fun initialize(slideDeck: SlideDeck, slidesService: Slides, driveService: Drive, presentationId: String?, copyId: String?): Presentation {
    // configure the logger level otherwise it get really verbose when gradle --info is configured
    val httpTransportLogger = Logger.getLogger(HttpTransport::class.java.name)
    httpTransportLogger.level = when {
      logger.isTraceEnabled -> Level.ALL
      logger.isDebugEnabled -> Level.CONFIG
      logger.isInfoEnabled -> Level.INFO
      logger.isWarnEnabled -> Level.WARNING
      else -> Level.SEVERE
    }
    val presentations = slidesService.presentations()
    val googleSlidesPresentation = getPresentation(driveService, slidesService, slideDeck.title, presentationId, copyId)

    val layouts = googleSlidesPresentation.layouts
    val slides = googleSlidesPresentation.slides

    // Create slides
    val batchUpdatePresentationRequest = BatchUpdatePresentationRequest()
    val requests = mutableListOf<Request>()
    deleteExistingSlides(slides, requests)

    addCreateTitleSlide(layouts, requests)
    logger.info("Layouts available: ${layouts.map { it.layoutProperties.name }} for presentation id: ${googleSlidesPresentation.presentationId}")

    for (slide in slideDeck.slides) {
      val layout = layouts.find { it.layoutProperties.name == slide.layoutId }
      if (layout == null) {
        logger.warn("Unable to find a layout for id: ${slide.layoutId} and slide: $slide")
      }
      addCreateSlideRequest(layout, UUID.randomUUID().toString(), requests)
    }
    batchUpdatePresentationRequest.requests = requests
    presentations.batchUpdate(googleSlidesPresentation.presentationId, batchUpdatePresentationRequest).execute()

    // Reload presentation
    return presentations.get(googleSlidesPresentation.presentationId)
      .setFields("title,layouts,masters,slides,presentationId")
      .execute()
  }

  private fun addTitle(pageElements: MutableList<PageElement>, slide: Slide, requests: MutableList<Request>) {
    val slideTitle = pageElements.find { it.shape.placeholder.type == "TITLE" || it.shape.placeholder.type == "CENTERED_TITLE" }
    if (slideTitle == null) {
      logger.warn("No TITLE or CENTERED_TITLE found on the slide elements: $pageElements, title: ${slide.title.orEmpty()} won't be added")
    } else {
      addInsertTextRequest(slideTitle.objectId, TextContent(slide.title.orEmpty()), 0, requests)
    }
  }

  private fun getPresentation(driveService: Drive, slidesService: Slides, title: String, presentationId: String?, copyId: String?): Presentation {
    return when {
      presentationId != null -> forPresentation(slidesService, presentationId)
      copyId != null -> copyPresentation(driveService, slidesService, title, copyId)
      else -> newPresentation(slidesService, title)
    }
  }

  private fun forPresentation(slidesService: Slides, presentationId: String): Presentation {
    logger.info("Get presentation for id: $presentationId")
    return slidesService.presentations().get(presentationId)
      .setFields("layouts,masters,slides,presentationId")
      .execute()
  }

  private fun copyPresentation(driveService: Drive, slidesService: Slides, title: String, copyId: String): Presentation {
    val driveFile = DriveFile()
    driveFile.name = title
    logger.info("Copy presentation from id: $copyId")
    val createdDriveFile = driveService.files().copy(copyId, driveFile).execute()
    return forPresentation(slidesService, createdDriveFile.id)
  }

  private fun newPresentation(slidesService: Slides, title: String): Presentation {
    val presentations = slidesService.presentations()
    var googleSlidesPresentation = Presentation().setTitle(title)
    // create a presentation
    googleSlidesPresentation = presentations
      .create(googleSlidesPresentation).setFields("layouts,masters,slides,presentationId")
      .execute()
    logger.info("Created presentation with id: ${googleSlidesPresentation.presentationId}")
    return googleSlidesPresentation
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
    val contentPerType = contents.groupBy { it.javaClass.simpleName }
    val images = contentPerType[ImageContent::class.simpleName] as List<ImageContent>?
    if (images != null && images.isNotEmpty()) {
      appendCreateImagesRequests(images, presentation, googleSlide, placeholder, requests)
    }
    addTextualContent(contents, placeholder.objectId, requests)
  }

  private fun addTextualContent(contents: List<SlideContent>, placeholderId: String, requests: MutableList<Request>) {
    var currentIndex = 0
    contents.filterNot { it is ImageContent }.forEachIndexed { index, content ->
      when (content) {
        is TextContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholderId, TextContent(text, content.ranges, content.roles, content.fontSize), currentIndex, requests)
          currentIndex += text.length
        }
        is ListContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholderId, TextContent(text, content.ranges, content.roles), currentIndex, requests)
          addCreateParagraphBullets(placeholderId, ListContent(text, content.type, content.ranges, content.roles), currentIndex, requests)
          currentIndex += text.length
        }
        is ListingContent -> {
          val text = if (index < contents.size) {
            content.text + "\n"
          } else {
            content.text
          }
          addInsertTextRequest(placeholderId, TextContent(text, ranges = content.ranges, fontSize = content.fontSize), currentIndex, requests)
          currentIndex += text.length
        }
      }
    }
  }

  private fun deleteExistingSlides(slides: List<Page>, requests: MutableList<Request>) {
    requests.addAll(slides.map {
      val objectId = slides.first().objectId
      val request = Request()
      val deleteObjectRequest = DeleteObjectRequest()
      deleteObjectRequest.objectId = objectId
      request.deleteObject = deleteObjectRequest
      request
    })
  }

  private fun addCreateTitleSlide(layouts: List<Page>, requests: MutableList<Request>) {
    val titleLayout = layouts.first { it.layoutProperties.name == "TITLE" }
    addCreateSlideRequest(titleLayout, UUID.randomUUID().toString(), requests)
  }

  private fun addCreateSlideRequest(layout: Page?, objectId: String, requests: MutableList<Request>) {
    val request = Request()
    val createSlide = CreateSlideRequest()
    if (layout != null) {
      val slideLayoutReference = LayoutReference()
      slideLayoutReference.layoutId = layout.objectId
      createSlide.slideLayoutReference = slideLayoutReference
    }
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
      if (type == "code" || type == "kbd") {
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
      } else if (type == "anchor" && textRange.token is AnchorToken) {
        val link = Link()
        link.url = textRange.token.target
        textStyle.link = link
        textStyle.underline = true
        val fgColor = OptionalColor()
        val fgOpaqueColor = OpaqueColor()
        val rgbColor = RgbColor()
        // #0063a3 - dark blue
        rgbColor.red = 0f
        rgbColor.green = .388f
        rgbColor.blue = .639f
        fgOpaqueColor.rgbColor = rgbColor
        fgColor.opaqueColor = fgOpaqueColor
        textStyle.foregroundColor = fgColor
      } else if (type == "underline") {
        textStyle.underline = true
      } else if (type == "big") {
        textStyle.underline = true
        val fontSizeDimension = Dimension()
        fontSizeDimension.magnitude = 20.0
        fontSizeDimension.unit = "PT"
        textStyle.fontSize = fontSizeDimension
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

object GoogleApi {
  private const val APPLICATION_NAME = "Asciidoctor Google Slides Converter"
  private val JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
  private const val TOKENS_DIRECTORY_PATH = "tokens"

  private val SCOPES = listOf(SlidesScopes.PRESENTATIONS, DriveScopes.DRIVE)

  fun getSlidesService(credentialsFilePath: String): Slides {
    // Build a new authorized API client service.
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    return Slides.Builder(httpTransport, JSON_FACTORY, getCredentials(credentialsFilePath, httpTransport))
      .setApplicationName(APPLICATION_NAME)
      .build()
  }

  fun getDriveService(credentialsFilePath: String): Drive {
    // Build a new authorized API client service.
    val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    return Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(credentialsFilePath, httpTransport))
      .setApplicationName(APPLICATION_NAME)
      .build()
  }

  /**
   * Creates an authorized Credential object.
   * @param httpTransport The network HTTP Transport.
   * @return An authorized Credential object.
   * @throws IOException If the credentials.json file cannot be found.
   */
  @Throws(IOException::class)
  private fun getCredentials(credentialsFilePath: String, httpTransport: NetHttpTransport): Credential {
    // Load client secrets.
    val credentialsFile = File(credentialsFilePath)
    if (credentialsFile.exists() && credentialsFile.canRead()) {
      FileReader(credentialsFile).use { fileReader ->
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, fileReader)

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(
          httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
          .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
          .setAccessType("offline")
          .build()
        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
      }
    } else {
      throw FileNotFoundException("Unable to read: $credentialsFilePath")
    }
  }
}
