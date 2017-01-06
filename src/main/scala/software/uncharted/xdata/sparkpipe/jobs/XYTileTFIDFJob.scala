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



import scala.util.{Failure, Success}
import com.typesafe.config.Config
import org.apache.spark.sql.SparkSession
import software.uncharted.sparkpipe.Pipe
import software.uncharted.xdata.ops.io.serializeElementDoubleScore
import software.uncharted.xdata.ops.salt.text.{TFIDFConfigurationParser, TextOperations}
import software.uncharted.xdata.sparkpipe.config.TileTopicConfig
import software.uncharted.xdata.sparkpipe.jobs.JobUtil.dataframeFromSparkCsv



object XYTileTFIDFJob extends AbstractJob {
  // Parse tile topic parameters out of supplied config
  private def parseTileTopicConfig (config: Config) = {
    TileTopicConfig(config) match {
      case Success(c) => c
      case Failure(e) =>
        error("Error getting tile config", e)
        sys.exit(-1)
    }
  }

  // Parse TF*IDF config parameters out of supplied config
  private def parseTFIDFConfig (config: Config) = {
    TFIDFConfigurationParser.parse(config) match {
      case Success(c) => c
      case Failure(e) =>
        error("Error getting TF*IDF config", e)
        sys.exit(-1)
    }
  }
  /**
    * This function actually executes the task the job describes
    *
    * @param session A spark session in which to run spark processes in our job
    * @param config The job configuration
    */
  override def execute(session: SparkSession, config: Config): Unit = {
    val schema = parseSchema(config)
    val tilingConfig = parseTilingParameters(config)
    val outputOperation = parseOutputOperation(config)
    val tileTopicConfig = parseTileTopicConfig(config)
    val tfidfConfig = parseTFIDFConfig(config)

    val projection = createProjection(tileTopicConfig.projectionConfig, tilingConfig.levels)
    val wordCloudTileOp = TextOperations.termFrequencyOp(
      tileTopicConfig.xColumn,
      tileTopicConfig.yColumn,
      tileTopicConfig.textColumn,
      projection,
      tilingConfig.levels
    )(_)
    // Create the dataframe from the input config
    val df = dataframeFromSparkCsv(config, tilingConfig.source, schema, session)

    // Process our data
    Pipe(df)
      .to(wordCloudTileOp)
      .to(TextOperations.doTFIDFByTileFast(tfidfConfig))
      .to(serializeElementDoubleScore)
      .to(outputOperation)
      .run
  }
}
