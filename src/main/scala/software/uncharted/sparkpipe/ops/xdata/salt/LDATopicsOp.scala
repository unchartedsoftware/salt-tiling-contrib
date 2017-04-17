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


package software.uncharted.sparkpipe.ops.xdata.salt

import org.apache.spark.rdd.RDD
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.core.util.SparseArray
import software.uncharted.sparkpipe.ops.xdata.text.analytics
import software.uncharted.sparkpipe.ops.xdata.text.analytics.{DictionaryConfig, LDAConfig}

import scala.collection.mutable

object LDATopicsOp {
  val tmpDir: String = "/tmp"

  /**
    * Perform LDA on the output of termFrequency, on a tile by tile basis, outputting the top topics in each tile
    *
    * This assumes a single bin per tile
    *
    * @param dictionaryConfig The dictionary creation configuration
    * @param ldaConfig The configuration for how to run LDA
    * @param input The input data of tiles of word bags
    * @tparam X The type of metadata associated with each tile
    * @return A new tile set containing the LDA results on each word bag
    */
  def ldaTopicsByTile[X] (dictionaryConfig: DictionaryConfig, ldaConfig: LDAConfig)
                         (input: RDD[SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]]):
  RDD[SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]] = {
    type InSeries  = SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]
    type OutSeries = SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]
    val transform: InSeries => Map[String, Int] = _.bins(0)

    analytics.wordBagLDA(dictionaryConfig, ldaConfig, transform)(input).map { case (inData, ldaResults) =>
      val outputResults = ldaResults.map { t =>
        (t.topic.map { ws => ws.word + ldaConfig.scoreSeparator + ws.score }.mkString(ldaConfig.wordSeparator), t.score)
      }.toList
      new OutSeries(
        inData.projection,
        inData.maxBin,
        inData.coords,
        SparseArray[List[(String, Double)]](1, List[(String, Double)](), 0.0f)(0 -> outputResults),
        inData.tileMeta)
    }
  }

  /**
    * Perform LDA on the output of termFrequency, on a tile by tile basis, outputting the top words in each tile,
    * weighted by topic weight and word-within-topic weight
    *
    * This assumes a single bin per tile
    *
    * @param dictionaryConfig The dictionary creation configuration
    * @param ldaConfig The configuration for how to run LDA
    * @param input The input data of tiles of word bags
    * @tparam X The type of metadata associated with each tile
    * @return A new tile set containing the LDA results on each word bag
    */
  def ldaWordsByTile[X] (dictionaryConfig: DictionaryConfig, ldaConfig: LDAConfig)
                        (input: RDD[SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]]):
  RDD[SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]] = {
    type InSeries  = SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]
    type OutSeries = SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]
    val transform: InSeries => Map[String, Int] = _.bins(0)

    analytics.wordBagLDA(dictionaryConfig, ldaConfig, transform)(input).map { case (inData, ldaResults) =>
      val wordScores = mutable.HashMap[String, Double]()
      ldaResults.foreach { t =>
        val topicScore = t.score
        t.topic.foreach { ws =>
          wordScores(ws.word) = wordScores.getOrElse(ws.word, 0.0) + topicScore * ws.score
        }
      }
      val outputResults = wordScores.toList.sortBy(-_._2).take(ldaConfig.wordsPerTopic)
      new OutSeries(
        inData.projection,
        inData.maxBin,
        inData.coords,
        SparseArray[List[(String, Double)]](1, List[(String, Double)](), 0.0f)(0 -> outputResults),
        inData.tileMeta)
    }
  }
}
