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
package software.uncharted.xdata.sparkpipe.config

import com.typesafe.config.{Config, ConfigException}
import grizzled.slf4j.Logging
import org.apache.spark.SparkContext
import org.apache.spark.broadcast.Broadcast
import software.uncharted.xdata.ops.topics.{TFIDF, WordDict}

case class TopicModellingParams (
  startDate: String,
  endDate: String,
  stopwords_bcst: Broadcast[Set[String]],
  iterN: Int,
  k: Int,
  alpha: Double,
  eta: Double,
  outdir: String,
  weighted: Boolean,
  tfidf_bcst: Option[Broadcast[Array[(String, String, Double)]]],
  hdfspath : String, // TODO done? either break off into seperate or nested config. depend if read function is generic or topics specific
  dateCol : String,
  idCol : String,
  textCol : String
)

/**
  * Parse the config object into arguments for the topic modelling pipeline operations
  */
object TopicModellingConfigParser extends Logging {
  def parse(config: Config, sc: SparkContext): TopicModellingParams = {
    try {
      // Load Data
      val loadConfig = config.getConfig("load") // XXX Split into two config option? one for loadTweets, one fo loadDates?
      val hdfspath = loadConfig.getString("hdfspath") // TODO rename path - is not specific to hdfs
      val dateCol = loadConfig.getString("dateColumn")
      val idCol = loadConfig.getString("idColumn")
      val textCol = loadConfig.getString("textColumn")

      val topicsConfig = config.getConfig("topics")
      val startDate = topicsConfig.getString("startDate")
      val endDate = topicsConfig.getString("endDate")
      val iterN = if (topicsConfig.hasPath("iterN")) topicsConfig.getInt("iterN") else 150
      val alpha = 1 / Math.E // topicsConfig.getDouble("alpha") // Interpreted by ConfigFactory as String, not Double
      val eta = if (topicsConfig.hasPath("eta")) topicsConfig.getDouble("eta") else 0.01
      val k = if (topicsConfig.hasPath("k")) topicsConfig.getInt("k") else 2
      val outdir = topicsConfig.getString("outdir")

      // LM INPUT DATA
      val swfiles : List[String] = topicsConfig.getStringList("stopWordFiles").toArray[String](Array()).toList // FIXME avoid cast. typesafe have a fix?
      val stopwords = WordDict.loadStopwords(swfiles) ++ Set("#isis", "isis", "#isil", "isil")
      val stopwords_bcst = sc.broadcast(stopwords)

      val tfidf_path = if (config.hasPath("tfidf_path")) config.getString("tfidf_path") else ""
      val weighted = if (tfidf_path != "") true else false
      val tfidf_bcst = if (weighted) {
        val tfidf_array = TFIDF.loadTfidf(tfidf_path, Array("2016-09-01")) // TODO parse start-end date into array of dates
        Some(sc.broadcast(tfidf_array))
      } else { None }

      TopicModellingParams(startDate, endDate, stopwords_bcst, iterN, k, alpha, eta, outdir, weighted, tfidf_bcst, hdfspath, dateCol, idCol, textCol)

    } catch {
      case e: ConfigException =>
        error(s"Failure parsing arguments from Topic Modelling configuration file", e)
        sys.exit(-1) // FIXME Move someplace else?
    }
  }
}
