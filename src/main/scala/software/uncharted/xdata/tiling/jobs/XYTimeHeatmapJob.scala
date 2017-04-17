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
import org.apache.spark.sql.{Column, SparkSession}
import software.uncharted.sparkpipe.Pipe
import software.uncharted.sparkpipe.ops.xdata.io.serializeBinArray
import software.uncharted.sparkpipe.ops.xdata.salt.{CartesianTimeHeatmap, MercatorTimeHeatmap}
import software.uncharted.xdata.tiling.config.{CartesianProjectionConfig, MercatorProjectionConfig, TilingConfig, XYTimeHeatmapConfig}
import software.uncharted.xdata.tiling.jobs.JobUtil.{dataframeFromSparkCsv, createMetadataOutputOperation}

// scalastyle:off method.length
object XYTimeHeatmapJob extends AbstractJob {
  // scalastyle:off cyclomatic.complexity

  def execute(sparkSession: SparkSession, config: Config): Unit = {
    val schema = parseSchema(config)
    val tilingConfig = parseTilingParameters(config)
    val outputOperation = parseOutputOperation(config)

    // Parse geo heatmap parameters out of supplied config
    val heatmapConfig = XYTimeHeatmapConfig.parse(config).getOrElse {
      logger.error("Invalid heatmap op config")
      sys.exit(-1)
    }

    val xyBoundsFound = heatmapConfig.projection.xyBounds match {
      case ara: Some[(Double, Double, Double, Double)] => true
      case None => false
      case _ => logger.error("Invalid XYbounds"); sys.exit(-1)
    }

    // create the heatmap operation based on the projection
    val heatmapOperation = heatmapConfig.projection match {
      case _: MercatorProjectionConfig => MercatorTimeHeatmap(
        heatmapConfig.yCol,
        heatmapConfig.xCol,
        heatmapConfig.timeCol,
        heatmapConfig.valueCol,
        if (xyBoundsFound) heatmapConfig.projection.xyBounds else None,
        heatmapConfig.timeRange,
        tilingConfig.levels,
        tilingConfig.bins.getOrElse(MercatorTimeHeatmap.defaultTileSize))(_)
      case _: CartesianProjectionConfig => CartesianTimeHeatmap(
        heatmapConfig.xCol,
        heatmapConfig.yCol,
        heatmapConfig.timeCol,
        heatmapConfig.valueCol,
        if (xyBoundsFound) heatmapConfig.projection.xyBounds else None,
        heatmapConfig.timeRange,
        tilingConfig.levels,
        tilingConfig.bins.getOrElse(CartesianTimeHeatmap.defaultTileSize))(_)
      case _ => logger.error("Unknown projection ${topicsConfig.projection}"); sys.exit(-1)
    }

    val seqCols = heatmapConfig.valueCol match {
      case None => Seq(heatmapConfig.xCol, heatmapConfig.yCol, heatmapConfig.timeCol)
      case _ => Seq(heatmapConfig.xCol, heatmapConfig.yCol, heatmapConfig.timeCol, heatmapConfig.valueCol.getOrElse(throw new Exception("Value column is not set")))
    }
    val selectCols = seqCols.map(new Column(_))

    // Pipe the dataframe
    Pipe(dataframeFromSparkCsv(config, tilingConfig.source, schema, sparkSession))
      .to(_.select(selectCols: _*))
      .to(_.cache())
      .to(heatmapOperation)
      .to(serializeBinArray)
      .to(outputOperation)
      .run()

    // create and save extra level metadata - the tile x,y,z dimensions in this case
    writeMetadata(config, tilingConfig, heatmapConfig)
  }

  private def writeMetadata(baseConfig: Config, tilingConfig: TilingConfig, heatmapConfig: XYTimeHeatmapConfig): Unit = {
    import net.liftweb.json.JsonAST._ // scalastyle:ignore
    import net.liftweb.json.JsonDSL._ // scalastyle:ignore

    val binCount = tilingConfig.bins.getOrElse(MercatorTimeHeatmap.defaultTileSize)
    val levelMetadata =
      ("bins" -> binCount) ~
      ("range" ->
        (("start" -> heatmapConfig.timeRange.min) ~
          ("count" -> heatmapConfig.timeRange.count) ~
          ("step" -> heatmapConfig.timeRange.step)))
    val jsonBytes = compactRender(levelMetadata).getBytes.toSeq
    createMetadataOutputOperation(baseConfig).foreach(_("metadata.json", jsonBytes))
  }
} // scalastyle:on cyclomatic.complexity