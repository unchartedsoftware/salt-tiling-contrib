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
package software.uncharted.xdata.ops.salt

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Column, DataFrame, Row}
import software.uncharted.salt.core.analytic.Aggregator
import software.uncharted.salt.core.generation.{Series, TileGenerator}
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.core.generation.request.TileRequest
import software.uncharted.salt.core.projection.numeric.NumericProjection
import software.uncharted.sparkpipe.Pipe
import software.uncharted.sparkpipe.ops.core.dataframe.castColumns

object ZXYOp extends ZXYOp {
  final val TILE_SIZE_DEFAULT = 256
}
/**
  * A superclass for operations which generate
  * zxy tile layers from a DataFrame, using Salt
  */
trait ZXYOp {
  // scalastyle:off parameter.number
  private val DOUBLE_TYPE = "double"

  def testCoordinateProjection[T, U, V, W, X](message: String,
                                              projection: NumericProjection[(Double, Double), (Int, Int, Int), (Int, Int)],
                                              tileSize: Int,
                                              xCol: String,
                                              yCol: String,
                                              vCol: String
                                             )(input: DataFrame): DataFrame = {

    val selectCols = Seq(xCol, yCol, vCol).map(new Column(_))

    // Use the pipeline to convert x/y cols to doubles, and select them along with v col first
    val frame = Pipe(input)
      .to(castColumns(Map(xCol -> DOUBLE_TYPE, yCol -> DOUBLE_TYPE)))
      .to(_.select(selectCols: _*))
      .run()


    // x and y will always be columns 0 and 1 respectively
    val cExtractor = (r: Row) => {
      if (!r.isNullAt(0) && !r.isNullAt(1)) {
        Some((r.getDouble(0), r.getDouble(1)))
      } else {
        None
      }
    }

    // v will always be column 2
    val vExtractor = (r: Row) => {
      if (!r.isNullAt(2)) {
        Some(r.getAs[T](2))
      } else {
        None
      }
    }

    val partials = frame.take(10)
    val maxBin = (tileSize - 1, tileSize - 1)

    // scalastyle:off regex
    println(message)
    partials.foreach { row =>
      val coords = cExtractor(row)
      val value = vExtractor(row)
      val projected = projection.project(coords, maxBin)
      println(s"Coordinates: $coords\t\tvalue: $value\t\tprojected coordinates: $projected") // scalastyle:ignore
    }
    // scalastyle:on regex

    input
  }
  /**
    * Main tiling operation.
    *
    * @param projection     a NumericProjection, which maps double x/y data-space coordinates into z/x/y tile coordinates
    * @param tileSize       the size of one side of a tile, in bins (i.e. 256 for a 256x256 tile)
    * @param xCol           the name of the x column
    * @param yCol           the name of the y column
    * @param vCol           the name of the value column (the value for aggregation)
    * @param vExtractor     Extracts value data from row
    * @param binAggregator  an Aggregator which aggregates values from the ValueExtractor
    * @param tileAggregator an optional Aggregator which aggregates bin values
    */
  def apply[T, U, V, W, X](projection: NumericProjection[(Double, Double), (Int, Int, Int), (Int, Int)],
                           tileSize: Int,
                           xCol: String,
                           yCol: String,
                           vCol: String,
                           vExtractor: (Row) => Option[T],
                           binAggregator: Aggregator[T, U, V],
                           tileAggregator: Option[Aggregator[V, W, X]]
                          )(request: TileRequest[(Int, Int, Int)])(input: DataFrame): RDD[SeriesData[(Int, Int, Int), (Int, Int), V, X]] = {

    val selectCols = Seq(xCol, yCol, vCol).map(new Column(_))

    // Use the pipeline to convert x/y cols to doubles, and select them along with v col first
    val frame = Pipe(input)
      .to(castColumns(Map(xCol -> DOUBLE_TYPE, yCol -> DOUBLE_TYPE)))
      .to(_.select(selectCols: _*))
      .run()

    // x and y will always be columns 0 and 1 respectively
    val cExtractor = (r: Row) => {
      if (!r.isNullAt(0) && !r.isNullAt(1)) {
        Some((r.getDouble(0), r.getDouble(1)))
      } else {
        None
      }
    }

    // create a series for our heatmap
    val series = new Series(
      (tileSize - 1, tileSize - 1),
      cExtractor,
      projection,
      vExtractor,
      binAggregator,
      tileAggregator
    )

    val sc = frame.sqlContext.sparkContext
    val generator = TileGenerator(sc)

    generator.generate(frame.rdd, series, request).flatMap(t => series(t))
  }
}
