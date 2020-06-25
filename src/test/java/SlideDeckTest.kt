import org.asciidoctor.Asciidoctor
import org.asciidoctor.googleslides.ListingContent
import org.asciidoctor.googleslides.SlideContent
import org.asciidoctor.googleslides.SlideDeck
import org.assertj.core.api.Assertions
import kotlin.test.Test

class SlideDeckTest {

  private val asciidoctor = Asciidoctor.Factory.create()

  @Test
  fun should_extract_speaker_notes() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/speaker-notes.adoc").readText(), mapOf())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    Assertions.assertThat(slides).isNotEmpty
    Assertions.assertThat(slides).hasSize(1)
    Assertions.assertThat(slides.first().speakerNotes).isEqualTo("Show the students: Neo4j Developer Manual: https://neo4j.com/docs/cypher-manual/current/ Cypher RefCard: http://neo4j.com/docs/cypher-refcard/current/")
  }
}
