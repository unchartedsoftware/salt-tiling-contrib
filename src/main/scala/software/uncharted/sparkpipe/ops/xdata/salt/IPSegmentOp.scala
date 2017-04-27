/**
  * Copyright © 2013-2017 Uncharted Software Inc.
  *
  * Property of Uncharted™, formerly Oculus Info Inc.
  *
  * http://uncharted.software/
  *
  * Released under the MIT License.
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  */
package software.uncharted.sparkpipe.ops.xdata.salt

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, Row}
import software.uncharted.salt.core.analytic.numeric.{MinMaxAggregator, SumAggregator}
import software.uncharted.salt.core.generation.{Series, TileGenerator}
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.core.generation.request.TileLevelRequest
import software.uncharted.salt.xdata.projection._
import software.uncharted.sparkpipe.ops.core.dataframe
import software.uncharted.sparkpipe.Pipe
import software.uncharted.xdata.tiling.config.{CartesianProjectionConfig, MercatorProjectionConfig, ProjectionConfig}

/**
  * An operation which generates tile layers of line segments, located from pairs of IP addresses, from a DataFrame,
  * using Salt
  */
// scalastyle:off method.length
// scalastyle:off cyclomatic.complexity
object IPSegmentOp {
  val DefaultTileSize = 256
  val StringType = "string"
  val leaderLineLength = 1024

  /**
    * Segment operation using the projection specified by projectionType with endpoints being IP addresses
    *
    * @param projectionType Type of Projection to use
    * @param arcType        The type of line projection specified by the ArcType enum
    * @param xyBounds       The min/max x and y bounds (minX, minY, maxX, maxY)
    * @param minSegLen      The minimum length of line (in bins) to project
    * @param maxSegLen      The maximum length of line (in bins) to project
    * @param ipFromCol      start IP address
    * @param ipToCol        end IP address
    * @param vCol           The name of the column which holds the value for a given row
    * @param zoomLevels     The zoom levels onto which to project
    * @param tileSize       The size of a tile in bins
    * @param input          The input data
    * @return An RDD of tiles
    */
  // scalastyle:off parameter.number
  def apply(projectionType: ProjectionConfig,
            arcType: ArcTypes.Value,
            xyBounds: Option[(Double, Double, Double, Double)],
            minSegLen: Option[Int],
            maxSegLen: Option[Int],
            ipFromCol: String,
            ipToCol: String,
            vCol: String,
            zoomLevels: Seq[Int],
            tileSize: Int = DefaultTileSize,
            tms: Boolean = true)(input: DataFrame):
  RDD[SeriesData[(Int, Int, Int), (Int, Int), Double, (Double, Double)]] = {


    val projection = projectionType match {
      case _: MercatorProjectionConfig => new IPSegmentProjection(
        zoomLevels,
        new MercatorLineProjection(
          zoomLevels,
          if (xyBounds.isDefined) (xyBounds.get._1, xyBounds.get._2) else MercatorLineProjection.mercatorMin,
          if (xyBounds.isDefined) (xyBounds.get._3, xyBounds.get._4) else MercatorLineProjection.mercatorMax,
          minSegLen, maxSegLen)
      )
      case _: CartesianProjectionConfig => {
        val minBounds = if (xyBounds.isDefined) (xyBounds.get._1, xyBounds.get._2) else (0.0, 0.0)
        val maxBounds = if (xyBounds.isDefined) (xyBounds.get._3, xyBounds.get._4) else (1.0, 1.0)

        new IPSegmentProjection(
          zoomLevels,
          arcType match {
            case ArcTypes.FullLine =>
              new SimpleLineProjection(zoomLevels, minBounds, maxBounds,
                minLengthOpt = minSegLen, maxLengthOpt = maxSegLen, tms = tms)
            case ArcTypes.LeaderLine =>
              new SimpleLeaderLineProjection(zoomLevels, minBounds, maxBounds, leaderLineLength,
                minLengthOpt = minSegLen, maxLengthOpt = maxSegLen, tms = tms)
            case ArcTypes.FullArc =>
              new SimpleArcProjection(zoomLevels, minBounds, maxBounds,
                minLengthOpt = minSegLen, maxLengthOpt = maxSegLen, tms = tms)
            case ArcTypes.LeaderArc =>
              new SimpleLeaderArcProjection(zoomLevels, minBounds, maxBounds, leaderLineLength,
                minLengthOpt = minSegLen, maxLengthOpt = maxSegLen, tms = tms)
          }
        )
      }
      case _ => throw new Exception("Unknown projection ${topicsConfig.projection}"); sys.exit(-1)
    }

    // Use the pipeline to convert x/y cols to doubles, and select them along with v col first
    val frame = Pipe(input).to(dataframe.castColumns(Map(ipFromCol -> StringType, ipToCol -> StringType))).run()

    val cExtractor = (r: Row) => {
      val fromIPIndex = r.schema.fieldIndex(ipFromCol)
      val toIPIndex = r.schema.fieldIndex(ipToCol)

      if (!r.isNullAt(fromIPIndex) && !r.isNullAt(toIPIndex)) {
        Some((r.getString(fromIPIndex), r.getString(toIPIndex)))
      } else {
        None
      }
    }

    val vExtractor = (r: Row) => {
      val rowIndex = r.schema.fieldIndex(vCol)
      if (!r.isNullAt(rowIndex)) {
        Some(r.getDouble(rowIndex))
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
      SumAggregator,
      Some(MinMaxAggregator)
    )

    val request = new TileLevelRequest(zoomLevels, (tc: (Int, Int, Int)) => tc._1)

    val sc = frame.sqlContext.sparkContext
    val generator = TileGenerator(sc)

    generator.generate(frame.rdd, series, request).flatMap(t => series(t))
  }
}
