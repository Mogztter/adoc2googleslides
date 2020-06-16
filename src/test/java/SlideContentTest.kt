import org.asciidoctor.Asciidoctor
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test


class SlideContentTest {

  private val asciidoctor = Asciidoctor.Factory.create()

  @Test
  fun should_map_ulist() {
    val document = asciidoctor.load(AsciidoctorSlides::class.java.getResource("/list-items-with-inline-styles.adoc").readText(), mapOf())
    val slideContent = SlideContent.map(document.findBy(mapOf("context" to ":ulist")).first())
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
  fun should_map_code_listing() {
    val document = asciidoctor.load(AsciidoctorSlides::class.java.getResource("/code-listing.adoc").readText(), mapOf())
    val slideContent = SlideContent.map(document.findBy(mapOf("context" to ":listing")).first())
    val listingContents = slideContent.contents.filterIsInstance(ListingContent::class.java)
    assertThat(listingContents).isNotEmpty
    assertThat(listingContents).hasSize(1)
    assertThat(listingContents.first().text).isEqualTo("""(A)-[:LIKES]->(B),(A)-[:LIKES]->(C),(B)-[:LIKES]->(C)(A)-[:LIKES]->(B)-[:LIKES]->(C)<-[:LIKES]-(A)""")
  }
}
