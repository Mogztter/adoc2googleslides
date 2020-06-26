package org.asciidoctor.googleslides.layout.algorithms

import org.asciidoctor.googleslides.layout.Item


interface Algorithm {
  fun sort(items: List<Item>): List<Item>
  fun placeItems(items: List<Item>): List<Item>
}

data class LeftRightAlgorithmConstraints(val maxItems: Int? = null)
class LeftRightAlgorithm(private val constraints: LeftRightAlgorithmConstraints? = null) : Algorithm {
  override fun sort(items: List<Item>): List<Item> {
    items.sortedBy { item: Item -> item.width }
    return items
  }

  override fun placeItems(items: List<Item>): List<Item> {
    return if (constraints?.maxItems != null) {
      var y = 0.0
      items.chunked(constraints.maxItems).flatMap { chunk ->
        val result = updateX(chunk, 0.0, y)
        y += result.maxBy { it.height }?.height!!
        result
      }
    } else {
      updateX(items, 0.0)
    }
  }

  private fun updateX(items: List<Item>, x: Double, y: Double = 0.0): List<Item> {
    var x1 = x
    return items.map { item ->
      item.x = x1
      item.y = y

      // Increment the x by the item's width
      x1 += item.width
      item
    }
  }
}
