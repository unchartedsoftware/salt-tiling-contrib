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
package software.uncharted.sparkpipe.ops.xdata.salt

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import software.uncharted.salt.core.analytic.numeric.{MinMaxAggregator, SumAggregator}
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.core.generation.request.TileLevelRequest
import software.uncharted.salt.core.projection.numeric.CartesianProjection

object CartesianHeatmapOp {

  def apply(// scalastyle:ignore
            xCol: String,
            yCol: String,
            valueCol: String,
            zoomLevels: Seq[Int],
            latLonBounds: Option[(Double, Double, Double, Double)] = None,
            tileSize: Int = ZXYOp.TILE_SIZE_DEFAULT
           )(input: DataFrame): RDD[SeriesData[(Int, Int, Int), (Int, Int), Double, (Double, Double)]] = {

    val projection = {
      if (latLonBounds.isEmpty) {
        new CartesianProjection(zoomLevels, (0, 0), (1, 1))
      } else {
        val geo_bounds = latLonBounds.get
        new CartesianProjection(zoomLevels, (geo_bounds._1, geo_bounds._2), (geo_bounds._3, geo_bounds._4))
      }
    }

    val request = new TileLevelRequest(zoomLevels, (tc: (Int, Int, Int)) => tc._1)

    ZXYOp(
      projection,
      tileSize,
      xCol,
      yCol,
      valueCol,
      SumAggregator,
      Some(MinMaxAggregator)
    )(request)(input)

  }
}
