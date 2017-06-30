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

package software.uncharted.contrib.tiling.jobs

import com.typesafe.config.Config
import org.apache.spark.sql.{Column, SparkSession}
import software.uncharted.sparkpipe.Pipe
import software.uncharted.sparkpipe.ops.contrib.io.serializeBinArray
import software.uncharted.sparkpipe.ops.contrib.salt.{HeatmapOp, TimeHeatmapOp}
import software.uncharted.contrib.tiling.config.{TilingConfig, XYTimeHeatmapConfig}
import software.uncharted.contrib.tiling.jobs.JobUtil.{createMetadataOutputOperation, dataframeFromSparkCsv}

/**
  * A job to do x,y,time coordinate based heatmap tiling.  The base heatmap tiles each store
  * a count in a given bin, whereas time-based tiles store an array of counts in each bin, with
  * each element being the counts for a given time bucket.  The job loads data from
  * HDFS, creates the time-based heatmap tiles, and writes the results out to the configured destination.
  */
object XYTimeHeatmapJob extends AbstractJob {

  def execute(sparkSession: SparkSession, config: Config): Unit = {
    val schema = parseSchema(config)
    val tilingConfig = parseTilingParameters(config)
    val outputOperation = parseOutputOperation(config)

    // Parse geo heatmap parameters out of supplied config
    // Parse xyTopic parameters out of supplied config
    val heatmapConfig = XYTimeHeatmapConfig.parse(config).recover { case err: Exception =>
      logger.error(s"Invalid '${XYTimeHeatmapConfig.rootKey}' config", err)
      sys.exit(-1)
    }.get

    val bins = tilingConfig.bins.getOrElse(HeatmapOp.DefaultTileSize)

    val projection = heatmapConfig.projection.createProjection(tilingConfig.levels)

    // create the heatmap operation based on the projection
    val heatmapOperation = TimeHeatmapOp(heatmapConfig.xCol, heatmapConfig.yCol, heatmapConfig.timeCol,
      heatmapConfig.valueCol, projection, heatmapConfig.timeRange, tilingConfig.levels, bins)(_)

    // list of columns we want to filter down to for the computation
    val selectCols = Seq(Some(heatmapConfig.xCol),
                         Some(heatmapConfig.yCol),
                         Some(heatmapConfig.timeCol),
                         heatmapConfig.valueCol).flatten.map(new Column(_))

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

    val binCount = tilingConfig.bins
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
