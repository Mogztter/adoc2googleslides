package org.asciidoctor.googleslides.layout

import com.google.api.services.slides.v1.model.PageElement
import com.google.api.services.slides.v1.model.Presentation
import javafx.geometry.BoundingBox

object GenericLayout {

  fun getBodyBoundingBox(presentation: Presentation, element: PageElement?): BoundingBox {
    if (element != null) {
      return calculateBoundingBox(element)
    }
    return BoundingBox(0.0, 0.0, presentation.pageSize.width.magnitude, presentation.pageSize.height.magnitude)
  }

  private fun calculateBoundingBox(element: PageElement): BoundingBox {
    val height = element.getSize().height.magnitude;
    val width = element.getSize().width.magnitude;
    val scaleX = element.transform.scaleX ?: 1.0
    val scaleY = element.transform.scaleY ?: 1.0
    val shearX = element.transform.shearX ?: 0.0
    val shearY = element.transform.shearY ?: 0.0
    return BoundingBox(
      element.transform.translateX,
      element.transform.translateY,
      (scaleX * width + shearX * height),
      scaleY * height + shearY * width
    )
  }
}
