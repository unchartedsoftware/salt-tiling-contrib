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

package software.uncharted.xdata.ops.topics

import org.apache.spark.broadcast.Broadcast

import scala.collection._ // wildcard nono FIXME
import java.io._
import grizzled.slf4j.Logging

// scalastyle:off public.methods.have.type
/**
  * NOTE:
  * This is essentially the same as the code in the standard BTM algorithm. The changes are:
  *     Sample Recorders
  *         the nonparametric Bayesian approach allows for unbounded values of K. This means
  *         that the sample recorders can expand (and contract!) to hold different numbers
  *         of 'topics'. As a first effort I have kept as much of the BTM code logic the same
  *         as possible but changed the sample recorders to using MutableArrays rather than Arrays
  *     MCMC sampling
  *         n.b. we use an MCMC to estimate the conditional posterior for an unbounded K
  *         draw topic index z using CRP + stick breaking
  *         if z = k_new, increment the number of topics (tables), add to sample recorders
  *         if nz(z) == 0 (i.e. if there are no samples assigned to topic z) remove the topic (table), remove from sample recorders
  *         returns K, theta, phi (standard BTM returns theta, phi)
  **/
class BDP(kK: Int) extends Serializable with Logging {
  def setLoggingLevel(ch.qos.logback.classic.Level level) {
    ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
    root.setLevel(level);
  }
  setLoggingLevel(ch.qos.logback.classic.Level.DEBUG)
  println(logger.isInfoEnabled)
  
  var k = kK
  var tfidf_dict: scala.collection.immutable.Map[Int,Double] = scala.collection.immutable.Map[Int,Double]()

//  def initTfidf(path: String, date: String, word_dict: scala.collection.immutable.Map[String, Int])= {
//    tfidf_dict = TFIDF.getTfidf_local(path, date, word_dict)
//  }

//  TODO explicit types
//  TODO Docstrings
  def initTfidf(tfidf_bcst: Broadcast[Array[(String, String, Double)]],
                date: String, word_dict: scala.collection.immutable.Map[String, Int]
               )= {
    val tfidf = tfidf_bcst.value
    tfidf_dict = TFIDF.filterTfidf(tfidf, date, word_dict)
    tfidf_dict
  }


  // ============================= MCMC sampling  ============================================
  def estimateMCMC(biterms:Array[Biterm], iterN: Int, model: SampleRecorder, m: Int, alpha: Double, eta: Double): (Int, Double) = {
    val start = System.nanoTime
    Iterator.range(0, iterN).foreach { iteration =>
      info(s"iteration: ${iteration + 1}\tk = ${k}")
      val bstart = System.nanoTime
      biterms.foreach { case b =>
        updateBiterm(b, model, m, alpha, eta)
      }
      // removeEmptyClusters & defrag
      k = model.defrag
      val bend = System.nanoTime
      val runningtime = (bend - bstart) / 1E9
      info("\t time: %.3f sec.".format(runningtime))
    }
    val duration = (System.nanoTime - start) / 1E9 / 60.0
    info(s"Elapsed time: %.4f min".format(duration))
    (k, duration)
  }

  def updateBiterm(b: Biterm, model: SampleRecorder, m: Int, alpha: Double, eta: Double) = {
    unsetTopic(model, b)
    val z = drawTopicIndex(model, b, m, alpha, eta)

    // increment value of K if z > k
    if (z == k) {
      info("\t+1\t")
      k = model.addCluster()
    }
    if (z >= k) info(s"\n\n WHY IS Z > K ? \nz is ${z} and k is ${k}\n")
    if (z > k) info("\n\n********\n z should not be greater than k !!!\n\n")
    setTopic(model, b, z)
  }

  def setTopic(model: SampleRecorder, b:Biterm, z:Int) = {
    b.z = z
    model.increment(z, b.biterm)
  }

  def unsetTopic(model: SampleRecorder, b:Biterm) = {
    model.decrement(b.biterm, b.z, k)
  }


  // =============================   MCMC Sampling   =============================
  // def drawTopicIndex(nwz: ArrayBuffer[Long], nz: ArrayBuffer[Long], b: Biterm, m: Int, alpha: Double, eta: Double) = {
  def drawTopicIndex(model: SampleRecorder, b: Biterm, m: Int, alpha: Double, eta: Double) = {
    val dist = conditionalPosterior(model, b.biterm, m, alpha, eta)
    val z = sample(dist)
    z
  }

  // calculate PDF: the conditional posterior for all k (i.e. the likelihood of generating biterm b)
  def conditionalPosterior(model: SampleRecorder, b: (Int, Int), m: Int, alpha: Double, eta: Double) = {
    val (w1, w2) = b
    // calculate f(•), the density of Mult(z) created by CRP
    def density(z: Int) = {
      val numerator = (model.getNwz(z, w1) + eta) * (model.getNwz(z, w1) + eta)
      val denominator = Math.pow( (2 * model.getNz(z) + m * eta), 2)
      val d = numerator.toDouble / denominator.toDouble
      model.getNz(z) * d
    }
    def density_new() = {
      val numerator = ( 0 + eta) * ( 0 + eta)
      val denominator = Math.pow( ( 0 + (m * eta) ), 2)
      alpha * (numerator / denominator)
    }
    val pd_existing = Iterator.range(0, k).map{ z => density(z) }.toArray
    val pd_new = density_new()
    val dist = pd_existing ++ Array(pd_new)
    val total = dist.sum
    val norm_dist = dist.map(_ * ( 100 / total / 100))
    val ordered_dist = norm_dist.zipWithIndex.sortWith(_._1 > _._1)     // associate each prob with its topic index, sort descending to make sampling more efficient
    ordered_dist
  }

  def sample[A](dist: Array[(Double, A)]): A = {
    val p = scala.util.Random.nextDouble
    val it = dist.iterator
    var accum = 0.0
    while (it.hasNext) {
      val (itemProb, item) = it.next
      accum += itemProb
      if (accum >= p)
        return item  // return so that we don't have to search through the whole distribution
    }
    sys.error(f"this should never happen")  // needed so it will compile
  }

  // ============================= Estimate parameters  =============================
  def calcTheta(model: SampleRecorder, n_biterms: Int, k: Int, alpha: Double) = {
    val theta = Iterator.range(0, k).map { z =>
      (model.getNz(z) + alpha) / (n_biterms + k * alpha)
    }.toArray
    theta
  }

  def calcPhi(model: SampleRecorder, m: Int, k: Int, beta: Double) = {
    val phi = Iterator.range(0, m).flatMap { w =>
      Iterator.range(0, k).map { z =>
        (model.getNwz(z, w) + beta) / (model.getNz(z) * 2 + m * beta)
      }
    }.toArray
    phi
  }

  def estimate_theta_phi(model: SampleRecorder, n_biterms: Int, m: Int, k: Int,  alpha: Double, beta: Double) = {
    val theta = calcTheta(model, n_biterms, k, alpha )
    val phi = calcPhi(model, m, k, beta )
    (theta, phi)
  }

  def fit(biterms: Array[Biterm], words: Array[String], iterN: Int, k: Int, alpha: Double, eta: Double, weighted: Boolean = false, topT: Int = 100 ) = {
    val m = words.size
//    val weighted: Boolean = false
    val SR = new SampleRecorder(m, k, weighted)
    if (weighted) { SR.setTfidf(tfidf_dict) }
    SR.initRecorders(biterms)
    val btmDp = new BDP(k)
    info("Running MCMC sampling...")
    val (newK, duration) = estimateMCMC(biterms, iterN, SR, m, alpha, eta)
    val n_biterms = biterms.size
    info("Calculating phi, theta...")
    val (theta, phi) = estimate_theta_phi(SR, n_biterms, m, newK, alpha, eta )
    info("Calculating topic distribution...")
    val topic_dist = BTMUtil.report_topics(theta, phi, words, m, newK, topT)     // take top words for a topic (default is top 100 words)
    val nzMap = SR.getNzMap.toMap[Int, Int]
    (topic_dist, theta, phi, nzMap, duration)
  }
}
