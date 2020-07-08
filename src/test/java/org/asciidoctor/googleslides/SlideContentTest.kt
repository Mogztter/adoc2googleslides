package org.asciidoctor.googleslides

import org.asciidoctor.Asciidoctor
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test


class SlideContentTest {

  private val asciidoctor = Asciidoctor.Factory.create()

  @Test
  fun should_extract_ulist() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/list-items-with-inline-styles.adoc").readText(), mapOf())
    val slideContent = SlideContent.from(document.findBy(mapOf("context" to ":ulist")).first())
    val listContents = slideContent.contents.filterIsInstance(ListContent::class.java)
    assertThat(listContents).isNotEmpty
    assertThat(listContents).hasSize(1)
    val listContent = listContents.first()
    assertThat(listContent.text).isEqualTo("""Declarative query language
Focuses on what, not how to retrieve
Uses keywords such as MATCH, WHERE, CREATE
Runs in the database server for the graph
ASCII art to represent nodes and relationships""")
    val textWithStyleRanges = listContent.ranges.filter { it.token.type != "text" }
    assertThat(textWithStyleRanges).hasSize(5)
    val whatEmphasisTextRange = textWithStyleRanges[0]
    assertThat(whatEmphasisTextRange.token.text).isEqualTo("what")
    assertThat(whatEmphasisTextRange.token.type).isEqualTo("em")
    assertThat(whatEmphasisTextRange.startIndex).isEqualTo(38)
    assertThat(whatEmphasisTextRange.endIndex).isEqualTo(42)
    val matchCodeTextRange = textWithStyleRanges[1]
    assertThat(matchCodeTextRange.token.text).isEqualTo("MATCH")
    assertThat(matchCodeTextRange.token.type).isEqualTo("code")
    assertThat(matchCodeTextRange.startIndex).isEqualTo(86)
    assertThat(matchCodeTextRange.endIndex).isEqualTo(91)
  }

  @Test
  fun should_extract_code_listing() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/code-listing.adoc").readText(), mapOf())
    val slideContent = SlideContent.from(document.findBy(mapOf("context" to ":listing")).first())
    val listingContents = slideContent.contents.filterIsInstance(ListingContent::class.java)
    assertThat(listingContents).isNotEmpty
    assertThat(listingContents).hasSize(1)
    assertThat(listingContents.first().text).isEqualTo("""(A)-[:LIKES]->(B),(A)-[:LIKES]->(C),(B)-[:LIKES]->(C)(A)-[:LIKES]->(B)-[:LIKES]->(C)<-[:LIKES]-(A)""")
  }

  @Test
  fun should_extract_kbd_macro() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/kbb-macro.adoc").readText(), mapOf("attributes" to mapOf("experimental" to "")))
    val slideContent = SlideContent.from(document.findBy(mapOf("context" to ":paragraph")).first())
    assertThat(slideContent.contents).hasSize(1)
    assertThat((slideContent.contents[0] as TextContent).ranges).hasSize(2)
    assertThat((slideContent.contents[0] as TextContent).ranges[1].token.type).isEqualTo("kbd")
    assertThat((slideContent.contents[0] as TextContent).ranges[1].token.text).isEqualTo(":play 4.0-neo4j-modeling-exercises")
  }

  @Test
  fun should_extract_admonition() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/admonition.adoc").readText(), mapOf())
    val slideContent = SlideContent.from(document.findBy(mapOf("context" to ":admonition")).first())
    assertThat(slideContent.contents).hasSize(1)
    assertThat((slideContent.contents[0] as TextContent).text).isEqualTo("NOTE: This exercise has 9 steps.\nEstimated time to complete: 30 minutes.")
  }

  @Test
  fun should_extract_interactive_checklist() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/interactive-checklist.adoc").readText(), mapOf())
    val node = document.findBy(mapOf("context" to ":ulist"))
    val slideContent = SlideContent.from(node.first())
    assertThat(slideContent.speakerNotes).isEqualTo("\nCorrect answer(s):\n- LOAD CSV\n")
    assertThat(slideContent.contents).hasSize(1)
    assertThat((slideContent.contents[0] as ListContent).text).isEqualTo("LOAD DATA\nIMPORT DATA\nLOAD CSV\nIMPORT CSV")
    assertThat((slideContent.contents[0] as ListContent).ranges).hasSize(4)
    // TODO: add assertions
  }
}
