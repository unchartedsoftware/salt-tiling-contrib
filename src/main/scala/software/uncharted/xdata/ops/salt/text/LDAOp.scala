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
package software.uncharted.xdata.ops.salt.text

import org.apache.spark.SparkContext
import software.uncharted.xdata.ops.util.BasicOperations
import software.uncharted.xdata.spark.mllib.MatrixUtilities

import scala.collection.mutable.{Map => MutableMap}
import org.apache.spark.mllib.clustering.{LDAModel, DistributedLDAModel, LDA, LocalLDAModel}
import org.apache.spark.sql.DataFrame
import org.apache.spark.mllib.linalg.{DenseVector, Matrix, SparseVector, Vector}
import org.apache.spark.rdd.RDD

/**
  * An operation to run Latent Dirichlet Allocation on texts in a corpus
  */
object LDAOp {
  val notWord = "('[^a-zA-Z]|[^a-zA-Z]'|[^a-zA-Z'])+"
  val tmpDir: String = "/tmp"

  /**
    * Take an RDD of (id, text) pairs, and transform the texts into vector references into a common dictionary, with the
    * entries being the count of words in that text.
    *
    * @param input
    * @return
    */
  def textToWordCount[T] (input: RDD[(T, String)]): (Map[String, Int], RDD[(T, Vector)]) = {
    val wordLists = input.map{case (id, text) =>
        val words = text.split(notWord).map(_.toLowerCase)
        val wordCounts = MutableMap[String, Int]()
        words.foreach(word => wordCounts(word) = wordCounts.getOrElse(word, 0) + 1)
      (id, wordCounts.toList.sorted.toArray)
    }
    // Get a list of all used words, in alphabetical order.
    val dictionary = wordLists.flatMap(_._2).map(_._1).distinct.collect.sorted.zipWithIndex.toMap

    // Port that dictionary back into our word maps, creating sparse vectors by map index
    val wordVectors = wordLists.map{case (id, wordList) =>
      val indices = wordList.map{case (word, count) => dictionary(word)}
      val values = wordList.map{case (word, count) => count.toDouble}
      val wordVector: Vector = new SparseVector(dictionary.size, indices, values)
      (id, wordVector)
    }
    (dictionary, wordVectors)
  }

  /**
    * Perform LDA analysis on documents in a dataframe
    *
    * @param idCol The name of a column containing a (long) id unique to each row
    * @param textCol The name of the column containing the text to analyze
    * @param k The number of topics to find
    * @param n The number of topics to record for each document
    * @param input The dataframe containing the data to analyze
    * @return The topics for each document
    */
  def lda (idCol: String, textCol: String, k: Int, w: Int, n: Int)(input: DataFrame): DataFrame = {
    val textRDD = input.select(idCol, textCol).rdd.map { row =>
      val id = row.getLong(0)
      val text = row.getString(1)
      (id, text)
    }

    // Perform our LDA analysis
    val rawResults = lda(k, w, n)(textRDD).map{case (index, text, scores) =>
      // Get rid of the text, and put the results in a product we can turn to a DataFrame easily
      DocumentTopics(index, scores)
    }

    // Reformat to a dataframe
    BasicOperations.toDataFrame(input.sqlContext)(rawResults)
  }

  /**
    * Perform LDA on an RDD of indexed documents.
 *
    * @param numTopics The number of topics to find
    * @param wordsPerTopic The number of words to keep per topic
    * @param topicsPerDocument The number of topics to record for each document
    * @param input An RDD of indexed documents; the Long id field should be unique for each row.
    * @return An RDD of the same documents, with a sequence of topics attached.  The third, attached, entry in each
    *         row should be read as Seq[(topic, topicScoreForDocument)], where the topic is
    *         Seq[(word, wordScoreForTopic)]
    */
  def lda (numTopics: Int, wordsPerTopic: Int, topicsPerDocument: Int)
          (input: RDD[(Long, String)]): RDD[(Long, String, Seq[TopicScore])] = {
    val sc = input.context

    // Figure out our dictionary
    val (dictionary, documents) = textToWordCount(input)
    val localDocs = documents.collect

    val model = getDistributedModel(sc, new LDA().setK(numTopics).setOptimizer("em").run(documents))

    // Unwind the topics matrix using our dictionary (but reversed)
    val allTopics = getTopics(model, dictionary, wordsPerTopic)

    // Doc id, topics, weights
    val topicsByDocument: RDD[(Long, Array[Int], Array[Double])] = model.topTopicsPerDocument(topicsPerDocument)

    // Unwind the topics for each document
    input.join(topicsByDocument.map { case (docId, topics, weights) =>
      // Get the top n topic indices
      val topTopics = topics.zip(weights).sortBy(-_._2).take(topicsPerDocument).toSeq

      // Expand these into their topic word vectors
      (docId, topTopics.map { case (index, score) =>
        TopicScore(allTopics(index), score)
      })
    }).map{case (id, (document, topics)) =>
      (id, document, topics)
    }
  }

  private def getDistributedModel (sc: SparkContext, model: LDAModel): DistributedLDAModel = {
    model match {
      case distrModel: DistributedLDAModel => distrModel
      case localModel: LocalLDAModel => {
        localModel.save(sc, tmpDir + "lda")
        DistributedLDAModel.load(sc, tmpDir + "lda")
      }
    }
  }

  private def getTopics (model: DistributedLDAModel, dictionary: Map[String, Int], wordsPerTopic: Int): Map[Int, Seq[WordScore]] = {
    val topics: Matrix = model.topicsMatrix
    val reverseDictionary = dictionary.map(_.swap)

    (0 until topics.numCols).map(c => (c, MatrixUtilities.column(topics, c))).map { case (topicIndex, topicVector) =>
      val wordScores = (topicVector match {
        case v: DenseVector =>
          v.values.zipWithIndex.map { case (value, index) =>
            WordScore(reverseDictionary(index), value)
          }
        case v: SparseVector =>
          v.indices.map(reverseDictionary(_)).zip(v.values).map{case (word, score) => WordScore(word, score)}
      }).sortBy(-_.score).take(wordsPerTopic).toSeq

      (topicIndex, wordScores)
    }.toMap
  }
}

case class WordScore (word: String, score: Double)
case class TopicScore (topic: Seq[WordScore], score: Double)
case class DocumentTopics (documentIndex: Long, topics: Seq[TopicScore])
