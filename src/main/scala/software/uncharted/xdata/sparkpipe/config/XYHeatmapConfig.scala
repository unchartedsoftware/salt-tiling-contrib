/**
  * Copyright (c) 2014-2015 Uncharted Software Inc. All rights reserved.
  *
  * Property of Uncharted(tm), formerly Oculus Info Inc.
  * http://uncharted.software/
  *
  * This software is the confidential and proprietary information of
  * Uncharted Software Inc. ("Confidential Information"). You shall not
  * disclose such Confidential Information and shall use it only in
  * accordance with the terms of the license agreement you entered into
  * with Uncharted Software Inc.
  */
package software.uncharted.xdata.sparkpipe.config

import com.typesafe.config.Config

import scala.util.Try
// scalastyle:ignore
case class XYHeatmapConfig(xCol: String,
                           yCol: String,
                           valueCol: String,
                           projection: ProjectionConfig)

// Parse config for geoheatmap sparkpipe op
object XYHeatmapConfig {
  val xyHeatmapKey = "xyHeatmap"
  val xColumnKey = "xColumn"
  val yColumnKey = "yColumn"
  val valueColumnKey = "valueColumn"

  def apply(config: Config): Try[XYHeatmapConfig] = {
    for (
      heatmapConfig <- Try(config.getConfig(xyHeatmapKey));
      projection <- ProjectionConfig(heatmapConfig)
    ) yield {
      XYHeatmapConfig(
        heatmapConfig.getString(xColumnKey),
        heatmapConfig.getString(yColumnKey),
        heatmapConfig.getString(valueColumnKey),
        projection
      )
    }
  }
}
