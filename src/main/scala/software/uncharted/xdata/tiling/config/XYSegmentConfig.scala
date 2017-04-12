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
package software.uncharted.xdata.tiling.config

import com.typesafe.config.Config
import software.uncharted.sparkpipe.ops.xdata.salt.ArcTypes

import scala.util.Try

// Parse config for segment sparkpipe op
case class XYSegmentConfig(arcType: ArcTypes.Value,
                           minSegLen: Option[Int] = None,
                           maxSegLen: Option[Int] = None,
                           x1Col: String,
                           y1Col: String,
                           x2Col: String,
                           y2Col: String,
                           valueCol: Option[String],
                           tileSize: Int,
                           projectionConfig: ProjectionConfig)

object XYSegmentConfig extends ConfigParser {
  private val xySegmentKey = "xySegment"
  private val arcTypeKey = "arcType"
  private val minSegLenKey = "minSegLen"
  private val maxSegLenKey = "maxSegLen"
  private val x1ColKey = "x1Column"
  private val y1ColKey = "y1Column"
  private val x2ColKey = "x2Column"
  private val y2ColKey = "y2Column"
  private val tileSizeKey = "tileSize"
  private val valueColumnKey = "valueColumn"

  def parse(config: Config): Try[XYSegmentConfig] = {
    for (
      segmentConfig <- Try(config.getConfig(xySegmentKey));
      projection <- ProjectionConfig.parse(segmentConfig)
    ) yield {
      val arcType: ArcTypes.Value = segmentConfig.getString(arcTypeKey).toLowerCase match {
        case "fullline" => ArcTypes.FullLine
        case "leaderline" => ArcTypes.LeaderLine
        case "fullarc" => ArcTypes.FullArc
        case "leaderarc" => ArcTypes.LeaderArc
      }
      XYSegmentConfig(
        arcType,
        if (segmentConfig.hasPath(minSegLenKey)) Some(segmentConfig.getInt(minSegLenKey)) else None,
        if (segmentConfig.hasPath(maxSegLenKey)) Some(segmentConfig.getInt(maxSegLenKey)) else None,
        segmentConfig.getString(x1ColKey),
        segmentConfig.getString(y1ColKey),
        segmentConfig.getString(x2ColKey),
        segmentConfig.getString(y2ColKey),
        getStringOption(segmentConfig, valueColumnKey),
        segmentConfig.getInt(tileSizeKey),
        projection
      )
    }
  }
}
