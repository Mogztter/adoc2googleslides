package org.asciidoctor.googleslides.layout.smiths

import jdk.nashorn.internal.objects.Global.Infinity
import org.asciidoctor.googleslides.layout.Item
import org.asciidoctor.googleslides.layout.algorithms.Algorithm
import kotlin.math.min

data class PackingStats(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

data class PackingExport(val height: Double, val width: Double, val items: List<Item>)

class PackingSmith(private val algorithm: Algorithm, options: Map<String, String>) {
  private val sort = options.getOrDefault("sort", "true").toBoolean()
  private val items = mutableListOf<Item>()

  fun addItem(item: Item) {
    items.add(item)
  }

  fun export(): PackingExport {
    val items = exportItems(items, sort, algorithm)
    val stats = getStats(items)
    return PackingExport(stats.maxY, stats.maxX, items)
  }

  companion object {
    /**
     * Method to normalize coordinates to 0, 0.
     */
    private fun normalizeCoordinates(items: List<Item>): List<Item> {
      var minX = Infinity
      var minY = Infinity
      for (item in items) {
        minX = min(minX, item.x)
        minY = min(minY, item.y)
      }
      for (item in items) {
        item.x -= minX;
        item.y -= minY;
      }
      return items
    }

    private fun getStats(items: List<Item>): PackingStats {
      // Get the endX and endY for each item
      val minXArr = items.map { it.x }
      val minYArr = items.map { it.y }
      val maxXArr = items.map { it.x + it.width }
      val maxYArr = items.map { it.y + it.height }
      return PackingStats(minXArr.max() ?: 0.0, minYArr.max() ?: 0.0, maxXArr.max() ?: 0.0, maxYArr.max() ?: 0.0)
    }

    private fun processItems(items: List<Item>, sort: Boolean, algorithm: Algorithm): List<Item> {
      return algorithm.placeItems(if (sort) {
        algorithm.sort(items)
      } else {
        items
      })
    }

    private fun exportItems(items: List<Item>, sort: Boolean, algorithm: Algorithm): List<Item> {
      return normalizeCoordinates(processItems(items, sort, algorithm))
    }
  }
}
