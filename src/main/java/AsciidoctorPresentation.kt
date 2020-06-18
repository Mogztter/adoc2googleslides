import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.SafeMode
import org.asciidoctor.ast.Document
import org.asciidoctor.ast.Section
import org.jsoup.Jsoup
import java.io.File

data class AsciidoctorPresentation(val title: String, val slides: List<AsciidoctorSlide>) {

  companion object {

    fun load(): AsciidoctorPresentation {
      val asciidoctor = Asciidoctor.Factory.create()
      val options = OptionsBuilder.options()
        .safe(SafeMode.SAFE)
        .attributes(AttributesBuilder.attributes()
          .attribute("imagesdir", "https://s3.amazonaws.com/dev.assets.neo4j.com/course/4.0-intro-neo4j/images/"))
        .baseDir(File("/home/guillaume/workspace/neo4j/adoc2googleslides/src/main/resources")).asMap()
      val document = asciidoctor.loadFile(File("/home/guillaume/workspace/neo4j/adoc2googleslides/src/main/resources/AllSlides.adoc"), options)
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
              val rightColumnContents = SlideContent.map(rightBlock)
              val leftColumnContents = SlideContent.map(leftBlock)
              AsciidoctorTitleAndTwoColumns(
                title = slideTitle,
                rightColumn = rightColumnContents,
                leftColumn = leftColumnContents,
                speakerNotes = speakerNotes + rightColumnContents.speakerNotes.orEmpty() + leftColumnContents.speakerNotes.orEmpty()
              )
            } else {
              println("WARNING: a two-columns block must have exactly 2 nested blocks, ignoring!")
              null
            }
          } else {
            val slideContents = contentBlocks.map { SlideContent.map(it) }
            val contents = if (slideContents.isNotEmpty()) {
              slideContents.reduce { slide, acc ->
                SlideContents(slide.contents + acc.contents, slide.speakerNotes.orEmpty() + acc.speakerNotes.orEmpty())
              }
            } else {
              SlideContents(emptyList())
            }
            AsciidoctorTitleAndBodySlide(slideTitle, contents, speakerNotes + contents.speakerNotes)
          }
        } else {
          null
        }
      }
      return AsciidoctorPresentation(document.doctitle, slides)
    }

    private fun flattenDocument(document: Document) {
      document.findBy(mapOf("context" to ":section")).filter {
        it.level > 1
      }.reversed().forEach { section  ->
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
