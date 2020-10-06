package org.asciidoctor.googleslides

import org.asciidoctor.Asciidoctor
import org.asciidoctor.OptionsBuilder
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import kotlin.test.Test

class SlideDeckTest {

  private val asciidoctor = Asciidoctor.Factory.create()

  @Test
  fun should_extract_speaker_notes() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/speaker-notes.adoc").readText(), OptionsBuilder.options().backend("googleslides").asMap())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(3)
    assertThat(slides[0].speakerNotes).containsExactly(
      SlideContents(contents = listOf(TextContent(text = "Show the students:", roles = listOf("notes")))),
      SlideContents(
        contents = listOf(
          ListContent(
            text = "Neo4j Developer Manual: https://neo4j.com/docs/cypher-manual/current/\nCypher RefCard: http://neo4j.com/docs/cypher-refcard/current/",
            type = "ulist",
            ranges = listOf(
              TextRange(token = TextToken(text = "Neo4j Developer Manual: "), startIndex = 0, endIndex = 24),
              TextRange(token = AnchorToken(text = "https://neo4j.com/docs/cypher-manual/current/", target = "https://neo4j.com/docs/cypher-manual/current/"), startIndex = 24, endIndex = 69),
              TextRange(token = TextToken(text = "Cypher RefCard: "), startIndex = 70, endIndex = 86),
              TextRange(token = AnchorToken(text = "http://neo4j.com/docs/cypher-refcard/current/", target = "http://neo4j.com/docs/cypher-refcard/current/"), startIndex = 86, endIndex = 131)
            ),
            roles = listOf("notes")
          )
        ),
        speakerNotes = listOf(SlideContents(listOf(TextContent(""))))
      ),
      SlideContents(listOf(TextContent("")))
    )
    assertThat(slides[1].speakerNotes).containsExactly(SlideContents(listOf(TextContent(""))))
    assertThat(slides[2].speakerNotes).containsExactly(
      SlideContents(
        listOf(
          TextContent(
            text = "The most widely used Cypher clause is MATCH.",
            ranges = listOf(
              TextRange(token = TextToken(text = "The most widely used Cypher clause is "), startIndex = 0, endIndex = 38),
              TextRange(token = TextToken(text = "MATCH", roles = listOf("code")), startIndex = 38, endIndex = 43),
              TextRange(token = TextToken(text = "."), startIndex = 43, endIndex = 44)
            ),
            roles = listOf("notes")
          )
        )
      ),
      SlideContents(
        listOf(
          TextContent(
            text = "Notice that the Cypher keywords MATCH and RETURN are upper-case.",
            ranges = listOf(
              TextRange(token = TextToken(text = "Notice that the Cypher keywords "), startIndex = 0, endIndex = 32),
              TextRange(token = TextToken(text = "MATCH", roles = listOf("code")), startIndex = 32, endIndex = 37),
              TextRange(token = TextToken(text = " and "), startIndex = 37, endIndex = 42),
              TextRange(token = TextToken(text = "RETURN", roles = listOf("code")), startIndex = 42, endIndex = 48),
              TextRange(token = TextToken(text = " are upper-case."), startIndex = 48, endIndex = 64)
            ),
            roles = listOf("notes")
          )
        )
      )
    )
  }

  @Test
  fun should_create_title_only_slide() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/title-only-layout.adoc").readText(), OptionsBuilder.options().backend("googleslides").asMap())
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
    val document = asciidoctor.loadFile(file, OptionsBuilder.options().backend("googleslides").asMap())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(55)
    assertThat(slides.map { it.title }).containsExactly(
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

  @Test
  fun should_flatten_real_world_presentation() {
    val file = File(SlideContentTest::class.java.getResource("/intro-graph-algo-neo4j.adoc").toURI())
    val document = asciidoctor.loadFile(file, OptionsBuilder.options().backend("googleslides").asMap())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(9)
    assertThat(slides.map { it.title }).containsExactly(
      "Course outline",
      "Overview of Graph Algorithms",
      "Graph Analytics and Algorithms",
      "When not to use graphs",
      "When not to use graph algorithms (2)",
      "Graph Structure",
      "Random network",
      "Structured network",
      "Summary"
    )

  }

  @Test
  fun should_extract_interactive_checklist() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/interactive-checklist.adoc").readText(), OptionsBuilder.options().backend("googleslides").asMap())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(1)
    assertThat(slides[0]).isInstanceOf(TitleAndBodySlide::class.java)
    assertThat((slides[0] as TitleAndBodySlide).body.contents).hasSize(2)
    assertThat(((slides[0] as TitleAndBodySlide).body.contents[1] as ListContent).text).isEqualTo("LOAD DATA\nIMPORT DATA\nLOAD CSV\nIMPORT CSV")
    assertThat(((slides[0] as TitleAndBodySlide).body.contents[1] as ListContent).ranges).hasSize(4)
  }

  @Test
  fun should_extract_nested_list() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/nested-list.adoc").readText(), OptionsBuilder.options().backend("googleslides").asMap())
    val slideDeck = SlideDeck.from(document)
    val slides = slideDeck.slides
    assertThat(slides).isNotEmpty
    assertThat(slides).hasSize(1)
    assertThat(slides[0]).isInstanceOf(TitleAndBodySlide::class.java)
    assertThat((slides[0] as TitleAndBodySlide).body.contents).hasSize(1)
    assertThat((slides[0] as TitleAndBodySlide).body.contents[0]).isInstanceOf(ListContent::class.java)
    val listContent = (slides[0] as TitleAndBodySlide).body.contents[0] as ListContent
    assertThat(listContent.ranges).containsExactly(
      TextRange(token = TextToken(text = "\u200DM"), startIndex = 15, endIndex = 17),
      TextRange(token = TextToken(text = "aze", roles = listOf("em")), startIndex = 17, endIndex = 20),
      TextRange(token = TextToken(text = " heart"), startIndex = 20, endIndex = 26),
      TextRange(token = TextToken(text = "\u200D\u200DReflection "), startIndex = 27, endIndex = 40),
      TextRange(token = TextToken(text = "pool", roles = listOf("strong")), startIndex = 40, endIndex = 44),
      TextRange(token = TextToken(text = "\u200DSecret "), startIndex = 45, endIndex = 53),
      TextRange(token = TextToken(text = "exit", roles = listOf("code")), startIndex = 53, endIndex = 57)
    )
    assertThat(listContent.text).isEqualTo("West wood maze\n\tMaze heart\n\t\tReflection pool\n\tSecret exit\nUntracked file in git repository")
  }
}
