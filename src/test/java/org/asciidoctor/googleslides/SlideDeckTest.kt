package org.asciidoctor.googleslides

import org.asciidoctor.Asciidoctor
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import java.io.File
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

  @Test
  fun should_flatten_presentation() {
    val file = File(SlideContentTest::class.java.getResource("/includes-flatten-order.adoc").toURI())
    val document = asciidoctor.loadFile(file, mapOf())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(55)
    assertThat(slides.map {it.title }).containsExactly(
      "About this course",
      "Lesson Overview",
      "Resources",
      "Introduction to Graph Data Modeling",
      "About this module",
      "What is graph data modeling?",
      "How does Neo4j support graph data modeling?",
      "Neo4j Property Graph Model",
      "Traversal in the graph",
      "Tools for graph data modeling",
      "Guided Exercise: Using the Arrow tool",
      "Workflow for graph data modeling",
      "Check your understanding",
      "Question 1",
      "Question 2",
      "Question 3",
      "Summary",
      "Designing the Initial Graph Data Model",
      "About this module",
      "Designing the initial data model",
      "Step 1: Understanding the domain",
      "Example domain: Bill of Materials",
      "Example BOM use cases",
      "Step 2: Create high-level sample data",
      "BOM high-level sample data",
      "Step 3: Define specific questions for the application",
      "Sample questions for the BOM",
      "Step 4 : Identify entities",
      "Identify entities from the questions",
      "Define properties",
      "Exercise 1: Identifying entities for the BOM application",
      "Exercise 1 solution",
      "Exercise 2: Creating the BOM entity model in the Arrow tool",
      "Exercise 2 solution",
      "Step 5: Identify connections between entities",
      "Identify connections between entities",
      "Naming relationships",
      "Direction and type",
      "How much fanout will a node have?",
      "Exercise 3: Adding relationships to the model",
      "Exercise 3 instructions",
      "Exercise 3 solution",
      "Example: Detailed sample data for the BOM application",
      "Step 6: Test the questions against the model",
      "Testing the model - 1",
      "Testing the model - 2",
      "Testing the model - 3",
      "Testing the model - 4",
      "Step 7: Test scalability",
      "Testing scalability",
      "Check your understanding",
      "Question 1",
      "Question 2",
      "Question 3",
      "Summary"
    )
  }
}
