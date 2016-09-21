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
package software.uncharted.xdata.sparkpipe.jobs

import com.typesafe.config.{Config, ConfigFactory}
import grizzled.slf4j.Logging
import software.uncharted.sparkpipe.Pipe
import software.uncharted.xdata.ops.salt.{CartesianSegmentOp}
import software.uncharted.xdata.sparkpipe.config.{Schema, SparkConfig, TilingConfig, XYSegmentConfig}
import software.uncharted.xdata.ops.io.serializeBinArray
import software.uncharted.xdata.sparkpipe.jobs.JobUtil.{createMetadataOutputOperation, createTileOutputOperation, dataframeFromSparkCsv}

// scalastyle:off method.length
object XYSegmentJob extends Logging {

  def execute(config: Config): Unit = {
     // parse the schema, and exit on any errors
     val schema = Schema(config).getOrElse {
       error("Couldn't create schema - exiting")
       sys.exit(-1)
     }

     // Parse tiling parameters out of supplied config
     val tilingConfig = TilingConfig(config).getOrElse {
       logger.error("Invalid tiling config")
       sys.exit(-1)
     }

     // Parse geo heatmap parameters out of supplied config
     val segmentConfig = XYSegmentConfig(config).getOrElse {
       logger.error("Invalid heatmap op config")
       sys.exit(-1)
     }

     // Parse output parameters and return the correspoding write function
     val outputOperation = createTileOutputOperation(config).getOrElse {
       logger.error("Output operation config")
       sys.exit(-1)
     }

    // create the segment operation based on the projection
    val segmentOperation = segmentConfig.projection match {
      case Some("cartesian") => CartesianSegmentOp(
        segmentConfig.arcType,
        segmentConfig.minSegLen,
        segmentConfig.maxSegLen,
        segmentConfig.x1Col,
        segmentConfig.y1Col,
        segmentConfig.x2Col,
        segmentConfig.y2Col,
        None,
        segmentConfig.xyBounds,
        segmentConfig.zBounds,
        segmentConfig.tileSize)(_)
      case _ => logger.error("Unknown projection ${topicsConfig.projection}"); sys.exit(-1)
    }

    // Create the spark context from the supplied config
    val sqlc = SparkConfig(config)
    try {
      // Create the dataframe from the input config
      // TODO Can we infer the schema? (if there is a header). Probably not, it'll get the types wrong
      val df = dataframeFromSparkCsv(config, tilingConfig.source, schema, sqlc)

      // Pipe the dataframe
      // TODO figure out all the correct stages
      Pipe(df)
         .to(_.select(segmentConfig.x1Col, segmentConfig.y1Col, segmentConfig.x2Col, segmentConfig.y2Col))
         .to(_.cache())
         .to(segmentOperation)
         .to(serializeBinArray)
         .to(outputOperation)
         .run()

      // Create and save extra level metadata - the tile x,y,z dimensions in this case
      // Can we make writeMetadata take only one config?
      writeMetadata(config, tilingConfig, segmentConfig)

    } finally {
      sqlc.sparkContext.stop()
    }
  }

  private def writeMetadata(baseConfig: Config, tilingConfig: TilingConfig, segmentConfig: XYSegmentConfig): Unit = {
    import net.liftweb.json.JsonDSL._ // scalastyle:ignore
    import net.liftweb.json.JsonAST._ // scalastyle:ignore

    // TODO: Verify these is the metadata we want
    val binCount = tilingConfig.bins.getOrElse(CartesianSegmentOp.defaultTileSize)
    val levelMetadata =
      ("bins" -> binCount) ~
      ("range" ->
          (("x1" -> segmentConfig.x1Col) ~
          ("y1" -> segmentConfig.y1Col) ~
          ("x2" -> segmentConfig.x2Col) ~
          ("y2" -> segmentConfig.y2Col)))

    val jsonBytes = compactRender(levelMetadata).getBytes.toSeq
    createMetadataOutputOperation(baseConfig).foreach(_("metadata.json", jsonBytes))
  }

  def main (args: Array[String]): Unit = {
    // get the properties file path
    if (args.length < 1) {
      logger.error("Path to conf file required")
      sys.exit(-1)
    }

    // load properties file from supplied URI
    val config = ConfigFactory.parseReader(scala.io.Source.fromFile(args(0)).bufferedReader()).resolve()
    execute(config)
  }
}
