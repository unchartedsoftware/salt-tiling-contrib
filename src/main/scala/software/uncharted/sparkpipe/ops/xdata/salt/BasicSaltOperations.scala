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
import org.apache.spark.sql.functions.{max, min}
import software.uncharted.salt.core.analytic.Aggregator
import software.uncharted.salt.core.analytic.numeric.CountAggregator
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.core.generation.request.{TileLevelRequest, TileRequest}
import software.uncharted.salt.core.generation.{Series, TileGenerator}

import scala.reflect.ClassTag

/**
  * Basic building-block operations from which other salt operations are built or composed.
  */
object BasicSaltOperations {
  def genericTiling[RT, DC, TC: ClassTag, BC, T, U, V, W, X] (series: Series[RT, DC, TC, BC, T, U, V, W, X])
                                                             (request: TileRequest[TC])
                                                             (data: RDD[RT]): RDD[SeriesData[TC, BC, V, X]] = {
    val generator = TileGenerator(data.sparkContext)
    generator.generate(data, series, request).flatMap(t => series(t))
  }

  def genericFullTilingRequest[RT, DC, TC: ClassTag, BC, T, U, V, W, X] (series: Series[RT, DC, TC, BC, T, U, V, W, X],
                                                                         levels: Seq[Int],
                                                                         getZoomLevel: TC => Int)
                                                                        (data: RDD[RT]): RDD[SeriesData[TC, BC, V, X]] = {
    genericTiling(series)(new TileLevelRequest[TC](levels, getZoomLevel))(data)
  }

  /**
    * Get the bounds of specified columns in a dataframe
    *
    * @param columns The columns to examine
    * @param data The raw data to examine
    * @return The minimum and maximum of xCol, then the minimum and maximum of yCol.
    */
  def getBounds (columns: String*)(data: DataFrame): Seq[(Double, Double)] = {
    val selects = columns.flatMap(column => Seq(min(column), max(column)))
    val minMaxes = data.select(selects:_*).take(1)(0).toSeq.map(_ match {
      case d: Double => d
      case f: Float => f.toDouble
      case l: Long => l.toDouble
      case i: Int => i.toDouble
    })

    minMaxes.grouped(2).map(bounds => (bounds(0), bounds(1))).toSeq
  }

  /**
    * Tile a dataset using a cartesian projection and a simple count aggregation
    *
    * @param xCol The column in which to find the X coordinate of the data
    * @param yCol The column in which to find the Y coordinate of the data
    * @param levels The levels to tile
    * @param boundsOpt The data bounds (minX, maxX, minY, maxY), or None to auto-detect data bounds
    * @param tileSize The size, in bins, of one output tile
    * @param input The input data
    * @return An RDD of tiles
    */
  def cartesianTiling (xCol: String, yCol: String, vCol: String, levels: Seq[Int],
                       boundsOpt: Option[(Double, Double, Double, Double)] = None,
                       tileSize: Int = ZXYOp.TILE_SIZE_DEFAULT)(input: DataFrame): RDD[SeriesData[(Int, Int, Int), (Int, Int), Double, Double]] = {
    val bounds = boundsOpt.getOrElse {
      val columnBounds = getBounds(xCol, yCol)(input)
      val (minX, maxX) = columnBounds(0)
      val (minY, maxY) = columnBounds(1)
      // Adjust upper bounds based on max level and bins
      val rangeX = maxX - minX
      val rangeY = maxY - minY
      val epsilon = 1.0 / ((1L << levels.max) * tileSize * 4)
      (minX, minY, maxX + rangeX * epsilon, maxY + rangeY * epsilon)
    }
    val getLevel: ((Int, Int, Int)) => Int = tileIndex => tileIndex._1
    val tileAggregation: Option[Aggregator[Double, Double, Double]] = None


    CartesianOp(
      tileSize, xCol, yCol, vCol, bounds, levels, CountAggregator, tileAggregation
    )(
      new TileLevelRequest[(Int, Int, Int)](levels, getLevel)
    )(input)
  }

  /**
    * Tile a dataset with segments using a cartesian projection
    *
    * @param x1Col The column in which to find the start X coordinate of the data
    * @param y1Col The column in which to find the start Y coordinate of the data
    * @param x2Col The column in which to find the end X coordinate of the data
    * @param y2Col The column in which to find the end Y coordinate of the data
    * @param levels The levels to tile
    * @param boundsOpt The data bounds (minX, maxX, minY, maxY), or None to auto-detect data bounds
    * @param tileSize The size, in bins, of one output tile
    * @param input The input data
    * @return An RDD of tiles
    */
  // scalastyle:off parameter.number
  def segmentTiling (x1Col: String, y1Col: String, x2Col: String, y2Col: String, levels: Seq[Int],
                     arcType: ArcTypes.Value,
                     minSegLen: Option[Int] = None,
                     maxSegLen: Option[Int] = None,
                     boundsOpt: Option[(Double, Double, Double, Double)] = None,
                     tileSize: Int = ZXYOp.TILE_SIZE_DEFAULT)(input: DataFrame): RDD[SeriesData[(Int, Int, Int), (Int, Int), Double, (Double, Double)]] = {
    val bounds = boundsOpt.getOrElse {
      val columnBounds = getBounds(x1Col, x2Col, y1Col, y2Col)(input)
      val (minX1, maxX1) = columnBounds(0)
      val (minX2, maxX2) = columnBounds(1)
      val (minY1, maxY1) = columnBounds(2)
      val (minY2, maxY2) = columnBounds(3)

      val minX = minX1 min minX2
      val maxX = maxX1 max maxX2
      val minY = minY1 min minY2
      val maxY = maxY1 max maxY2

      // Adjust upper bounds based on max level and bins
      val rangeX = maxX - minX
      val rangeY = maxY - minY
      val epsilon = 1.0 / ((1L << levels.max) * tileSize * 4)
      (minX, minY, maxX + rangeX * epsilon, maxY + rangeY * epsilon)
    }

    val tileAggregation: Option[Aggregator[Double, Double, Double]] = None

    CartesianSegmentOp(
      arcType,
      minSegLen,
      maxSegLen,
      x1Col,
      y1Col,
      x2Col,
      y2Col,
      None,
      Some(bounds),
      levels.min to levels.max,
      tileSize
    )(input)
  }

}
