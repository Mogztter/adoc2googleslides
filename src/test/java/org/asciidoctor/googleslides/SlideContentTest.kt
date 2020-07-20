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
    val textWithStyleRanges = listContent.ranges.filter { it.token.roles.isNotEmpty() }
    assertThat(textWithStyleRanges).hasSize(5)
    val whatEmphasisTextRange = textWithStyleRanges[0]
    assertThat(whatEmphasisTextRange.token.text).isEqualTo("what")
    assertThat(whatEmphasisTextRange.token.roles).containsExactly("em")
    assertThat(whatEmphasisTextRange.startIndex).isEqualTo(38)
    assertThat(whatEmphasisTextRange.endIndex).isEqualTo(42)
    val matchCodeTextRange = textWithStyleRanges[1]
    assertThat(matchCodeTextRange.token.text).isEqualTo("MATCH")
    assertThat(matchCodeTextRange.token.roles).containsExactly("code")
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
    assertThat((slideContent.contents[0] as TextContent).ranges[1].token.roles).containsExactly("kbd")
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
    assertThat(slideContent.speakerNotes).contains(SlideContents(listOf(TextContent("\nCorrect answer(s):\n- LOAD CSV\n"))))
    assertThat(slideContent.contents).hasSize(1)
    assertThat((slideContent.contents[0] as ListContent).text).isEqualTo("LOAD DATA\nIMPORT DATA\nLOAD CSV\nIMPORT CSV")
    assertThat((slideContent.contents[0] as ListContent).ranges).hasSize(4)
  }

  @Test
  fun should_extract_simple_table() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/table.adoc").readText(), mapOf())
    val node = document.findBy(mapOf("context" to ":table"))
    val slideContent = SlideContent.from(node.first())
    assertThat(slideContent.contents).hasSize(1)
    assertThat(slideContent.contents[0]).isInstanceOf(TableContent::class.java)
    assertThat((slideContent.contents[0] as TableContent).columns).isEqualTo(4)
    assertThat((slideContent.contents[0] as TableContent).rows).containsExactly(
      TableRow(listOf(
        TableCell("Products", "header"),
        TableCell("Parts", "header"),
        TableCell("Assemblies", "header"),
        TableCell("Notes", "header"))
      ),
      TableRow(listOf(TableCell("Wood table 40\"", "body"),
        TableCell("Wood top 40\"", "body"),
        TableCell("Leg assembly", "body"),
        TableCell("Has 4 legs", "body"))
      ),
      TableRow(listOf(
        TableCell("Deluxe wood table 40\"", "body"),
        TableCell("Glass top 40\"", "body"),
        TableCell("Leg assembly", "body"),
        TableCell("Has 4 legs", "body"))),
      TableRow(listOf(
        TableCell("Wood table 60\"", "body"),
        TableCell("Wood top 60\"", "body"),
        TableCell("Leg assembly", "body"),
        TableCell("Has 6 legs, table brace", "body")
      )),
      TableRow(listOf(
        TableCell("Deluxe wood table 60\"", "body"),
        TableCell("Glass top 60\"", "body"),
        TableCell("Leg assembly", "body"),
        TableCell("Has 6 legs, table brace", "body")
      )),
      TableRow(listOf(
        TableCell("", "body"),
        TableCell("Leg", "body"),
        TableCell("", "body"),
        TableCell("", "body")
      )),
      TableRow(listOf(
        TableCell("", "body"),
        TableCell("Leg foot", "body"),
        TableCell("", "body"),
        TableCell("", "body")
      )),
      TableRow(listOf(
        TableCell("", "body"),
        TableCell("M20 bolt", "body"),
        TableCell("", "body"),
        TableCell("", "body")
      )),
      TableRow(listOf(
        TableCell("", "body"),
        TableCell("M20 nut", "body"),
        TableCell("", "body"),
        TableCell("", "body")
      )),
      TableRow(listOf(
        TableCell("", "body"),
        TableCell("Leg plate", "body"),
        TableCell("", "body"),
        TableCell("Uses 2 bolts/nuts per leg", "body")
      )),
      TableRow(listOf(
        TableCell("", "body"),
        TableCell("Table brace", "body"),
        TableCell("", "body"),
        TableCell("", "body")
      ))
    )
  }

  @Test
  fun should_set_open_block_roles_on_table() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/table.adoc").readText(), mapOf())
    val node = document.findBy(mapOf("context" to ":open"))
    val slideContent = SlideContent.from(node.first())
    val tableContents = slideContent.contents.filterIsInstance<TableContent>()
    assertThat(tableContents).hasSize(1)
    assertThat(tableContents[0]).isInstanceOf(TableContent::class.java)
    assertThat(tableContents[0].columns).isEqualTo(4)
    assertThat(tableContents[0].roles).containsExactly("smaller")
  }

  @Test
  fun should_extract_cumulative_styles() {
    val document = asciidoctor.load(SlideContentTest::class.java.getResource("/cumulative-styles.adoc").readText(), mapOf())
    val nodes = document.findBy(mapOf("context" to ":paragraph"))
    val firstTextContent = SlideContent.from(nodes[0]).contents[0] as TextContent
    assertThat(firstTextContent.ranges).containsExactly(
      TextRange(
        TextToken(
          text = "Identify connections between entities.",
          roles = listOf("big", "strong")
        ),
        startIndex = 0,
        endIndex = 38
      )
    )
    val secondTextContent = SlideContent.from(nodes[1]).contents[0] as TextContent
    assertThat(secondTextContent.ranges).containsExactly(
      TextRange(token = TextToken(text = "This is "), startIndex = 0, endIndex = 8),
      TextRange(token = TextToken(text = "awesome", roles = listOf("em", "strong")), startIndex = 8, endIndex = 15),
      TextRange(token = TextToken(text = "!"), startIndex = 15, endIndex = 16)
    )
  }
}
