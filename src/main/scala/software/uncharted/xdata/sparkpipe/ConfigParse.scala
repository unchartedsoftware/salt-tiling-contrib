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
package software.uncharted.xdata.sparkpipe

import com.typesafe.config.{Config, ConfigException}
import grizzled.slf4j.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}
import software.uncharted.xdata.ops.io.BinArraySerializerOp
import software.uncharted.xdata.ops.salt.RangeDescription

import scala.collection.JavaConverters.asScalaSetConverter

// Parse spark configuration and instantiate context from it
object SparkConfig {
  def apply(config: Config): SQLContext = {
    val sparkConfig = config.getConfig("spark")
    val conf = new SparkConf()
    sparkConfig.entrySet().asScala.foreach(e => conf.set(s"spark.${e.getKey}", e.getValue.unwrapped().asInstanceOf[String]))
    val sc = new SparkContext(conf)
    new SQLContext(sc)
  }
}

// Parse tiling parameter and store results
case class TilingConfig(levels: Int, xBins: Int, yBins: Int, source: String)
object TilingConfig extends Logging {
  def apply(config: Config): Option[TilingConfig] = {
    try {
      val tilingConfig = config.getConfig("tiling")
      Some(TilingConfig(tilingConfig.getInt("levels"), tilingConfig.getInt("xBins"), tilingConfig.getInt("yBins"), tilingConfig.getString("source")))
    } catch {
      case e: ConfigException =>
        error("Failure parsing arguments from [tiling]", e)
        None
    }
  }
}

// Parse output configuration and return output function
object OutputConfig {
  def apply(config: Config): (RDD[((Int, Int, Int), Seq[Byte])]) => RDD[((Int, Int, Int), Seq[Byte])] = {
    if (config.hasPath("fileOutput")) {
      val serializerConfig = config.getConfig("fileOutput")
      BinArraySerializerOp.binArrayFileStoreOp(serializerConfig.getString("dest"), serializerConfig.getString("layer"))
    } else if (config.hasPath("s3Output")) {
      val serializerConfig = config.getConfig("s3Output")
      BinArraySerializerOp.binArrayS3StoreOp(sys.env("AWS_ACCESS_KEY"), sys.env("AWS_SECRET_KEY"),
        serializerConfig.getString("bucket"), serializerConfig.getString("layer"))
    } else {
      throw new ConfigException.Missing("Failure parsing output - [s3Output] or [fileOutput] required")
    }
  }
}

// Parse config for geoheatmap sparkpipe op
case class GeoHeatmapConfig(lonCol: String, latCol: String, timeCol: String, timeRange: RangeDescription[Long], timeFormat: Option[String] = None)
object GeoHeatmapConfig extends Logging {
  def apply(config: Config): Option[GeoHeatmapConfig] = {
    try {
      val geoHeatmapConfig = config.getConfig("geoHeatmap")
      Some(GeoHeatmapConfig(
        geoHeatmapConfig.getString("longitudeColumn"),
        geoHeatmapConfig.getString("latitudeColumn"),
        geoHeatmapConfig.getString("timeColumn"),
        RangeDescription.fromMin(geoHeatmapConfig.getLong("min"), geoHeatmapConfig.getLong("step"), geoHeatmapConfig.getInt("count")),
        if (geoHeatmapConfig.hasPath("timeFormat")) Some(geoHeatmapConfig.getString("timeFormat")) else None)
      )
    } catch {
      case e: ConfigException =>
        error("Failure parsing arguments from [geoHeatmap]", e)
        None
    }
  }
}
