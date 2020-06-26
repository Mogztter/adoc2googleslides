package org.asciidoctor.googleslides.layout

import org.asciidoctor.googleslides.layout.algorithms.LeftRightAlgorithm
import org.asciidoctor.googleslides.layout.algorithms.LeftRightAlgorithmConstraints
import org.asciidoctor.googleslides.layout.smiths.PackingSmith

data class Item(val height: Int, val width: Int, var x: Double = 0.0, var y: Double = 0.0, val meta: Map<String, Any?> = emptyMap())

object Layout {

  fun get(options: Map<String, String>): PackingSmith {
    return PackingSmith(LeftRightAlgorithm(LeftRightAlgorithmConstraints(maxItems = 2)), options)
  }
}
