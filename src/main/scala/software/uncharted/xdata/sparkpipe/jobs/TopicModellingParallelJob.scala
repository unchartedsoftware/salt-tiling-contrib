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
import org.apache.spark.sql.DataFrame
import software.uncharted.sparkpipe.Pipe
import software.uncharted.sparkpipe.ops.core.dataframe.temporal.parseDate
import org.apache.spark.rdd.RDD
import software.uncharted.xdata.ops.topics.{BDPParallel}
import software.uncharted.xdata.ops.salt.{CartesianTimeHeatmap, MercatorTimeHeatmap}
import software.uncharted.xdata.sparkpipe.config.{Schema, SparkConfig, TilingConfig, XYTimeHeatmapConfig}
import software.uncharted.xdata.sparkpipe.jobs.JobUtil.{createMetadataOutputOperation, createTileOutputOperation, dataframeFromSparkCsv}

// scalastyle:off method.length
object TopicModellingJob extends Logging {

  /*
  val path = "/xdata/data/twitter/isil-keywords/2016-09/isil_keywords.2016090 "
  val dates = Array("2016-09-03", "2016-09-04", "2016-09-05")
  val bdp = RunBDPParallel(sc)
  val rdd = bdp.loadTSVTweets(path, dates, 1, 0, 6)
   */

  def run(
    rdd: RDD[Array[String]],
    dates: Array[String],
    stopwords_bcst: Broadcast[Set[String]],
    iterN: Int,
    k: Int,
    alpha: Double,
    eta: Double,
    outdir: String,
    weighted: Boolean = false,
    tfidf_bcst: Broadcast[Array[(String, String, Double)]] = null
  ) = {
    // group records by date
    val kvrdd = BDPParallel.keyvalueRDD(rdd)
    // partition data by date
    val partitions = kvrdd.partitionBy(new DatePartitioner(dates))
    // run BTM on each partition
    val parts = partitions.mapPartitions { iter => BDPParallel.partitionBDP(iter, stopwords_bcst, iterN, k, alpha, eta, weighted, tfidf_bcst) }.collect

    // Compute Coherence Scores for each of the topic distibutions
    // define number of top words to use to compute coherence score
    val topT = 10
    val cparts = JobUtil.castResults(parts)
    cparts.foreach { cp =>
      val (date, topic_dist, theta, phi, nzMap, m, duration) = cp
      val topic_terms = topic_dist.map(x => x._2.toArray)
      val textrdd = rdd.filter(x => x(0) == date).map(x => x(2))
      // takes a long time to calculate Coherence. Uncomment to enable // TODO make configurable
      // val (cs, avg_cs) = Coherence.computeCoherence(textrdd, topic_terms, topT)
      // output_results(topic_dist, nzMap, theta, phi, date, iterN, m, alpha, eta, duration, outdir, cs.toArray, avg_cs)         // n.b. outputing coherence scores as well
      JobUtil.output_results(topic_dist, nzMap, theta, phi, date, iterN, m, alpha, eta, duration, outdir)
    }
  }

  def main(args: Array[String]): Unit = {
    // get the properties file path
    if (args.length != 1) {
      logger.error("Usage: ") // TODO
      sys.exit(-1)
    }

    // load properties file from supplied URI
    val config = ConfigFactory.parseReader(scala.io.Source.fromFile(args(0)).bufferedReader()).resolve()
    val params = TopicModellingConfigParser.parse(config)

    run(
      params.rdd,
      params.dates,
      params.stopwords_bcst,
      params.iterN,
      params.k,
      params.alpha,
      params.eta,
      params.outdir,
      params.weighted,
      params.tfidf_bcst
    )
  }
