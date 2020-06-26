package org.asciidoctor.googleslides.image

data class RemoteImageDefinition(
  val url: String,
  val type: String?,
  val width: Double,
  val height: Double,
  val style: String?,
  val padding: Double = 0.0,
  val offsetX: Double = 0.0,
  val offsetY: Double = 0.0
)
