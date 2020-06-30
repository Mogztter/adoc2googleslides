package org.asciidoctor.googleslides

import org.asciidoctor.Asciidoctor
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import kotlin.test.Test

class SlideDeckTest {

  private val asciidoctor = Asciidoctor.Factory.create()

  @Test
  fun should_extract_speaker_notes() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/speaker-notes.adoc").readText(), mapOf())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(3)
    assertThat(slides[0].speakerNotes).isEqualTo("Show the students: Neo4j Developer Manual: https://neo4j.com/docs/cypher-manual/current/ Cypher RefCard: http://neo4j.com/docs/cypher-refcard/current/")
    assertThat(slides[1].speakerNotes).isEqualTo("")
    assertThat(slides[2].speakerNotes).isEqualTo("""The most widely used Cypher clause is MATCH.
Notice that the Cypher keywords MATCH and RETURN are upper-case.""")
  }

  @Test
  fun should_create_title_only_slide() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/title-only-layout.adoc").readText(), mapOf())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(3)
    assertThat(slides[1]).isInstanceOf(TitleOnlySlide::class.java)
    assertThat((slides[1] as TitleOnlySlide).layoutId).isEqualTo("TITLE_ONLY_1")
    assertThat((slides[1] as TitleOnlySlide).title).isEqualTo("Check your understanding")
  }
}
