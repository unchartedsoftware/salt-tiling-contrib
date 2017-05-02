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

package software.uncharted.xdata.tiling.jobs

import com.typesafe.config.Config
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Column, SparkSession}
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.sparkpipe.Pipe
import software.uncharted.sparkpipe.ops.xdata.io.serializeBinArray
import software.uncharted.sparkpipe.ops.xdata.salt.{CartesianSegmentOp, MercatorSegmentOp}
import software.uncharted.xdata.tiling.config.{CartesianProjectionConfig, MercatorProjectionConfig, XYSegmentConfig}
import software.uncharted.xdata.tiling.jobs.JobUtil.{dataframeFromSparkCsv, createMetadataOutputOperation}

/**
  * Executes the a segment job configured given a path to a configuration file
  *
  * Takes a csv input of pickup points [e.g (lat,lon)] and dropoff points and draws a line between them
  *
  * Outputs tiles in binary format to specified output directory using z/x/y format
  *
  * Refer to resources/xysegment/xysegment.conf for an example configuration file of all the options available
  */
// scalastyle:off method.length
object XYSegmentJob extends AbstractJob {
  // scalastyle:off cyclomatic.complexity
  def execute(sparkSession: SparkSession, config: Config): Unit = {
    val schema = parseSchema(config)
    val tilingConfig = parseTilingParameters(config)
    val outputOperation = parseOutputOperation(config)

    // Parse geo heatmap parameters out of supplied config
    val segmentConfig = XYSegmentConfig.parse(config).recover { case err: Exception =>
      logger.error(s"Invalid '${XYSegmentConfig.rootKey}' config", err)
      sys.exit(-1)
    }.get

    // create the segment operation based on the projection
    val segmentOperation = segmentConfig.projectionConfig match {
      case _: MercatorProjectionConfig => MercatorSegmentOp(
        segmentConfig.minSegLen,
        segmentConfig.maxSegLen,
        segmentConfig.x1Col,
        segmentConfig.y1Col,
        segmentConfig.x2Col,
        segmentConfig.y2Col,
        segmentConfig.valueCol,
        segmentConfig.projectionConfig.xyBounds,
        tilingConfig.levels,
        tilingConfig.bins.getOrElse(MercatorSegmentOp.DefaultTileSize))(_)
      case _: CartesianProjectionConfig => CartesianSegmentOp(
        segmentConfig.arcType,
        segmentConfig.minSegLen,
        segmentConfig.maxSegLen,
        segmentConfig.x1Col,
        segmentConfig.y1Col,
        segmentConfig.x2Col,
        segmentConfig.y2Col,
        segmentConfig.valueCol,
        segmentConfig.projectionConfig.xyBounds,
        tilingConfig.levels,
        tilingConfig.bins.getOrElse(CartesianSegmentOp.DefaultTileSize))(_)
      case _ => logger.error("Unknown projection ${segmentConfig.projectionConfig}"); sys.exit(-1)
    }

    val seqCols = segmentConfig.valueCol match {
      case None => Seq(segmentConfig.x1Col, segmentConfig.y1Col, segmentConfig.x2Col, segmentConfig.y2Col)
      case _ => Seq(
        segmentConfig.x1Col,
        segmentConfig.y1Col,
        segmentConfig.x2Col,
        segmentConfig.y2Col,
        segmentConfig.valueCol.getOrElse(throw new Exception("Value column is not set")))
    }
    val selectCols = seqCols.map(new Column(_))

    // Pipe the dataframe
    Pipe(dataframeFromSparkCsv(config, tilingConfig.source, schema, sparkSession))
      .to(_.select(selectCols: _*))
      .to(_.cache())
      .to(segmentOperation)
      .to(writeMetadata(config))
      .to(serializeBinArray)
      .to(outputOperation)
      .run()
  }

  private def writeMetadata[BC, V](baseConfig: Config)
                                  (tiles: RDD[SeriesData[(Int, Int, Int), BC, V, (Double, Double)]]):
  RDD[SeriesData[(Int, Int, Int), BC, V, (Double, Double)]] = {
    import net.liftweb.json.JsonAST._ // scalastyle:ignore
    import net.liftweb.json.JsonDSL._ // scalastyle:ignore

    val metadata = tiles
      .map(tile => {
        val (level, minMax) = (tile.coords._1, tile.tileMeta.getOrElse((0.0, 0.0)))
        level.toString -> (("min" -> minMax._1) ~ ("max" -> minMax._2))
      })
      .collect()
      .toMap


    val jsonBytes = compactRender(metadata).getBytes.toSeq
    createMetadataOutputOperation(baseConfig).foreach(_("metadata.json", jsonBytes))

    tiles
  }  // scalastyle:on cyclomatic.complexity
}
