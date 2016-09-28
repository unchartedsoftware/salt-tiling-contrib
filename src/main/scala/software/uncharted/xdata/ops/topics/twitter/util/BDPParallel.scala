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

package software.uncharted.xdata.ops.topics.twitter.util

import java.io.Serializable

import grizzled.slf4j.Logging
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row

/**
  * Difference between BDP_parallel and BDP:
  *  - DatePartitioner class
  *  -convert texts to key value pairs (date, text) or (date, (id, text))
  * Pretty much everything else is the same and runs the sam as BDP, just on separate partiions.
  */
object BDPParallel extends Serializable with Logging {

  /**
    * Main Topic Modeling Function
    */
  // scalastyle:off parameter.number
  //def partitionBDP(iterator: Iterator[(String, (String, String))],
  // stpbroad: Broadcast[scala.collection.immutable.Set[String]], iterN: Int, k:Int, alpha: Double
  // , eta: Double, weighted: Boolean = false, tfidf_path: String = "") = {
  def partitionBDP(
    iterator: Iterator[Row],
    stpbroad: Broadcast[scala.collection.immutable.Set[String]],
    iterN: Int,
    k:Int,
    alpha: Double,
    eta: Double,
    textCol: String,
    tfidf_bcst: Option[Broadcast[Array[(String, String, Double)]]] = None
  ) : Iterator[Option[Array[Any]]] = {
    if (iterator.isEmpty) {
      info("Empty partition. Revisit your partitioning mechanism to correct this skew warning")
      Iterator(None)
    } else {
      val datetexts = iterator.toSeq.map(x => (x(x.fieldIndex("_ymd_date")), x(x.fieldIndex(textCol))))
      val date = datetexts.head._1.asInstanceOf[String]
      val texts : Array[String] = datetexts.map(x => x._2.asInstanceOf[String]).distinct.toArray // no retweets

      val stopwords = stpbroad.value
      val minCount = 0
      val (word_dict, words) = WordDict.createWordDictLocal(texts, stopwords, minCount)
      val m = words.length

      val bdp = new BDP(k)

      val biterms0 = texts.map(text => BTMUtil.extractBitermsFromTextRandomK(text, word_dict, stopwords.toSet, k)).flatMap(x => x)
      val biterms = biterms0

      var weighted = false
      if (tfidf_bcst.isDefined) {
        bdp.initTfidf(tfidf_bcst.get, date, word_dict)
        weighted = true
      }

      val (topic_dist, theta, phi, nzMap, duration) = bdp.fit(biterms, words, iterN, k, alpha, eta, weighted)
      Iterator(Some(Array(date, topic_dist, theta, phi, nzMap, m, duration)))
    }
  }

  def keyvalueRDD(rdd: RDD[Array[String]]) = {
    rdd.map(x => (x(0), (x(1), x(2))))
  }
}
