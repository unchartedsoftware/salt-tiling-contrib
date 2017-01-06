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

import java.io.FileInputStream

import scala.collection.mutable.{Map => MutableMap}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import software.uncharted.salt.core.analytic.Aggregator
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.core.generation.request.TileLevelRequest
import software.uncharted.salt.core.projection.numeric.NumericProjection
import software.uncharted.salt.core.util.SparseArray
import software.uncharted.xdata.ops.salt.ZXYOp
import software.uncharted.xdata.sparkpipe.config.LDAConfig

import scala.reflect.ClassTag


/**
  * Various generic text operations, not specific to a single textual analytic
  */
object TextOperations extends ZXYOp {
  type TileData[T, X] = SeriesData[(Int, Int, Int), (Int, Int), T, X]
  private val notWord = "('[^a-zA-Z]|[^a-zA-Z]'|[^a-zA-Z'])+"

  private def nullToOption[T] (t: T): Option[T] =
    if (null == t) {
      None
    } else {
      Some(t)
    }

  private def getWordFile (fileNameOpt: Option[String], caseSensitive: Boolean): Option[Set[String]] = {
    fileNameOpt.map { fileName =>
      val fileStream =
        nullToOption(getClass.getResourceAsStream(fileName))
        .getOrElse(new FileInputStream(fileName))

      val rawWords = scala.io.Source.fromInputStream(fileStream).getLines.map(_.trim)
      if (caseSensitive) {
        rawWords.toSet
      } else {
        rawWords.map(_.toLowerCase).toSet
      }
    }
  }


  private def documentToWordBag (document: String,
                                 caseSensitive: Boolean,
                                 stopWords: Option[Set[String]],
                                 goWords: Option[Set[String]]): Map[String, Int] = {
    val wordCounts = MutableMap[String, Int]()
    document.split(notWord).foreach { uncasedWord =>
      val word =
        if (caseSensitive) {
          uncasedWord.trim
        } else {
          uncasedWord.trim.toLowerCase
        }
      if (
        stopWords.map(!_.contains(word)).getOrElse(true) &&
        goWords.map(_.contains(word)).getOrElse(true)
      ) {
        wordCounts(word) = wordCounts.getOrElse(word, 0) + 1
      }
    }

    wordCounts.toMap
  }


  /**
    * Take a dataframe with a column containing text documents, and tile the documents into term-frequency collections.
    *
    * @param xCol The column with the x coordinate of each record
    * @param yCol The column with the y coordinate of each record
    * @param textCol The column containing the document in each record
    * @param projection The projection dermining how x and y coordinates are interpretted
    * @param zoomLevels The tile levels to generate
    * @param input The input data
    * @return A set of tiles containing, by tile, the words appearing in each tile, and the number of times each word
    *         appears
    */
  def termFrequencyOp(xCol: String,
                      yCol: String,
                      textCol: String,
                      projection: NumericProjection[(Double, Double), (Int, Int, Int), (Int, Int)],
                      zoomLevels: Seq[Int])
                     (input: DataFrame):
  RDD[SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], Nothing]] = {
    // Pull out term and document frequencies for each tile
    val request = new TileLevelRequest(zoomLevels, (tc: (Int, Int, Int)) => tc._1)
    val binAggregator = new WordCounter

    super.apply(projection, 1, xCol, yCol, textCol, binAggregator, None)(request)(input)
  }

  /**
    * Convert an input DataFrame into an RDD of word bags
    *
    * @param config A dictionary configuration object that specifies some aspects of how word conversion takes place
    * @param idColumn The column of the input DataFrame containing a unique record ID for each row
    * @param documentColumn The column of the input DataFrame containing the documents to be analyzed
    * @param input The input DataFrame
    * @return An RDD of word bags, indexed by record ID
    */
  def dataframeToWordBags (config: DictionaryConfiguration,
                           idColumn: String,
                           documentColumn: String)
                          (input: DataFrame): RDD[(Any, Map[String, Int])] = {
    val stopWords = getWordFile(config.stopwords, config.caseSensitive)
    val goWords = getWordFile(config.dictionary, config.caseSensitive)
    input.
      // Pull out our document column
      select(idColumn, documentColumn).rdd.map(row => (row.get(0), row.getString(1).trim)).
      // Get rid of null documents
      filter(!_._2.isEmpty).
      // Change simple document strings to word bags
      map { case (id, document) => (id, documentToWordBag(document, config.caseSensitive, stopWords, goWords)) }
  }

  /**
    * Convert an RDD of T to an RDD of word bags
    *
    * @param config A dictionary configuration object that specifies some aspects of how word conversion takes place
    * @param idExtractorFcn A function to extract the ID from each record
    * @param documentExtractorFcn A function to extract a document from each record
    * @param input The input record collection
    * @tparam I The ID type of each record
    * @tparam T The type of input record
    * @return An RDD of word bags, indexed by record ID
    */
  def rddToWordBags[I, T] (config: DictionaryConfiguration,
                           idExtractorFcn: T => I,
                           documentExtractorFcn: T => String)
                          (input: RDD[T]): RDD[(I, Map[String, Int])] = {
    val stopWords = getWordFile(config.stopwords, config.caseSensitive)
    val goWords = getWordFile(config.dictionary, config.caseSensitive)
    input.
      // Pull out our document column
      map(t => (idExtractorFcn(t), documentExtractorFcn(t).trim)).
      // Get rid of null documents
      filter(!_._2.isEmpty).
      // Change simple document strings to word bags
      map { case (id, document) => (id, documentToWordBag(document, config.caseSensitive, stopWords, goWords)) }
  }

  /**
    * Take a dataframe containing documents, and transform it to a tile set of word bags - transforming each document
    * into a word bag, then combining them.
    *
    * @param xColumn The column of the input DataFrame containing the x coordinate of each document
    * @param yColumn The column of the input DataFrame containing the y coordinate of each document
    * @param documentColumn  The column of the input DataFrame containing the documents to be analyzed
    * @param projection The projection from (x, y) to tile space
    * @param zoomLevels The zoom levels to tile
    * @param input The input data
    * @return
    */
  def tileWordBags(xColumn: String,
                   yColumn: String,
                   documentColumn: String,
                   projection: NumericProjection[(Double, Double), (Int, Int, Int), (Int, Int)],
                   zoomLevels: Seq[Int])
                  (input: DataFrame):
  RDD[TileData[Map[String, Int], Nothing]] = {
    // Pull out term and document frequencies for each tile
    val request = new TileLevelRequest(zoomLevels, (tc: (Int, Int, Int)) => tc._1)
    val binAggregator = new WordCounter

    super.apply(projection, 1, xColumn, yColumn, documentColumn, binAggregator, None)(request)(input)
  }

  /**
    * Create a dictionary of the terms seen in a set of documents, along with the term frequency (the number of
    * documents in which it occurs) for each term
    *
    * @param config A description of which words are to be chosen
    * @param wordBagExtractorFcn A function to pull a word bag from each input record
    * @param wordBags The input data, already processed into word bags by dataFrameToWordBags or rddToWordBags
    * @return The dictionary to use with this set of word bags
    */
  def getDictionary[T] (config: DictionaryConfiguration,
                        wordBagExtractorFcn: T => Map[String, Int])
                       (wordBags: RDD[T]): Array[(String, Int)] = {
    val docCount = config.needDocCount.map(yes => wordBags.count)
    val minDF = config.minDF.map(_ * docCount.getOrElse(0L))
    val maxDF = config.maxDF.map(_ * docCount.getOrElse(0L))

    val dictionaryRDD = wordBags.map(wordBagExtractorFcn).
      // ... Flatten out word bags, adding in document count
      flatMap(_.map { case (word, count) => (word, 1) }).
      // ... total document counts
      reduceByKey(_ + _).
      // ... weed out words that appear too infrequently or too often
      filter {
      case (word, documentCount) =>
        (minDF.map(_ <= documentCount).getOrElse(true) && maxDF.map(_ >= documentCount).getOrElse(true))
    }

    config.maxFeatures.map { max =>
      // We've declared a maximum number of words, take the top N words
      dictionaryRDD.sortBy(-_._2).take(max)
    }.getOrElse {
      // No word maximum; take them all.
      dictionaryRDD.collect
    }.sorted
  }

  // Helper function for getDictionaries.  This limits values to indexed minima and maxima - so if the index of the
  // value is n, the value has to lie between minDF(n) and maxDF(n)
  // Basically, this is only outside getDictionaries to reduce it's complexity so it passes scalastyle tests.  That
  // being said this does make sense as a separate function, so does actually reduce complexity, so I guess scalastyle
  // works... sort-of.
  private def rangeLimitation (minDF: Option[Map[Int, Double]], maxDF: Option[Map[Int, Double]])
                              (documentCounts: Map[Int, Int])
                              (docData: (Int, Int)): Option[(Int, Int)] = {
    val (docIndex, termCount) = docData

    if (
      minDF.map(_ (docIndex) <= documentCounts(docIndex)).getOrElse(true) &&
        maxDF.map(_ (docIndex) >= documentCounts(docIndex)).getOrElse(true)
    ) {
      Some((docIndex, termCount))
    } else {
      None
    }
  }

  /**
    * Create a dictionary of the terms seen in a set of documents, along with the term frequency (the number of
    * documents in which it occurs) for each term
    *
    * @param config A description of which words are to be chosen
    * @param wordBagExtractorFcn A function to pull a dictionary index and a document (in the form of a word bag) from
    *                            each input record,  All documents with the same dictionary index will contribute to
    *                            the same dictionary.
    * @param input The input data, already processed into word bags by dataFrameToWordBags or rddToWordBags
    * @tparam T The type of input record
    * @return The dictionary to use with this set of word bags
    */
  def getDictionaries[T] (config: DictionaryConfiguration,
                                  wordBagExtractorFcn: T => (Int, Map[String, Int]))
                                 (input: RDD[T]): Array[(String, Map[Int, Int])] = {
    val indexedDocuments = input.map(wordBagExtractorFcn)

    val docCounts = config.needDocCount.map { yes =>
      indexedDocuments.map { case (docIndex, doc) => (docIndex, 1) }.reduceByKey(_ + _).collect.toMap
    }
    val minDF = config.minDF.map(min => docCounts.get.map { case (docIndex, docCount) => (docIndex, docCount * min) })
    val maxDF = config.maxDF.map(max => docCounts.get.map { case (docIndex, docCount) => (docIndex, docCount * max) })
    val limitToRange: Map[Int, Int] => ((Int, Int)) => Option[(Int, Int)] = rangeLimitation(minDF, maxDF)(_)

    val dictionaryRDD = indexedDocuments.flatMap { case (docIndex, docWords) =>
      // ... Flatten out word bags, adding in document count
      docWords.map { case (word, count) => (word, MutableMap(docIndex -> 1)) }
    }.reduceByKey { (a, b) =>
      // ... total document counts, by document index, in place.
      b.foreach { case (docIndex, count) => a(docIndex) = a.getOrElse(docIndex, 0) + count }
      a
    }.map { case (term, documentCounts) =>
      // ... weed out terms that appear too frequently or infrequently
      docCounts.map { dc =>
        (term, documentCounts.flatMap(limitToRange(dc)(_)).toMap)
      }.getOrElse((term, documentCounts.toMap))
    }.filter(_._2.size > 0)

    config.maxFeatures.map { maxFeatures =>
      dictionaryRDD.sortBy(-_._2.values.max).take(maxFeatures)
    }.getOrElse(
      dictionaryRDD.collect
    ).sortBy(_._1)
  }

  /**
    * Convert an RDD of word bags into an RDD of word vectors (i.e., eliminate references to the strings, make it
    * all numeric).  Word vectors are still stored sparsely (i.e., as maps)
    *
    * @param data The collection of word bags (word, term frequency) to convert
    *             ``  * @param dictionary The dictionary to use to convert them.  Dictionary form is (word, document frequency)
    * @return A new collection of word bags in the form of (index within dictionary, term frequency)
    */
  def wordBagToWordVector (data: RDD[(Any, Map[String, Int])],
                           dictionary: Array[(String, Int)]): RDD[(Any, Map[Int, Int])] = {
    val dictionaryLookup = dictionary.map(_._1).zipWithIndex.toMap
    data.map { case (id, wordBag) =>
      (id, wordBag.flatMap { case (word, termFrequency) =>
        dictionaryLookup.get(word).map(index => (index, termFrequency))
      })
    }
  }

  /**
    * Perform TFIDF on the output of termFrequency, on a tile by tile basis
    *
    * This version operates level by level; it is easier to understand, but slower than version 2 below.
    *
    * @param config Configuration specifying how to perform TF*IDF analytic
    * @param input
    * @return
    */
  def doTFIDFByTileSlow[X](config: TFIDFConfiguration)(input: RDD[TileData[Map[String, Int], X]])
  : RDD[TileData[List[(String, Double)], X]] = {
    // Cache input - we're going to be going through it a lot
    input.cache()
    val levels = input.map(_.coords._1).distinct.collect().sorted
    val results = for (level <- levels) yield {
      val lvlData = input.filter(_.coords._1 == level)
      val lvlDocs = lvlData.flatMap(_.bins.seq).filter(!_.isEmpty).count
      val lvlDict = getDictionary[Map[String, Int]](config.dictionaryConfig, map => map)(lvlData.flatMap(_.bins.seq))
      val lvlIdfs = lvlDict.map { case (term, documentsWithTerm) =>
        (term, config.idf.inverseDocumentFrequency(lvlDocs, documentsWithTerm))
      }.toMap
      tileTransformationOp((binData: Map[String, Int], tile: (Int, Int, Int), bin: (Int, Int)) => {
        if (binData.isEmpty) {
          List[(String, Double)]()
        } else {
          val maxRawFrequency = binData.map(_._2).max
          val terms = binData.size
          binData.map { case (term, rawFrequency) =>
            val tf = config.tf.termFrequency(rawFrequency, terms, maxRawFrequency)
            val idf = lvlIdfs(term)
            (term, tf * idf)
          }.toList.sortBy(_._2).take(config.wordsToKeep)
        }
      })(lvlData)
    }
    results.reduce(_ union _)
  }

  /**
    * Perform TFIDF on the output of termFrequency, on a tile by tile basis
    *
    * This version acts on all levels at once, so is faster than the level by level version, but more complex.
    *
    * @param config Configuration specifying how to perform TF*IDF analytic
    * @param input
    * @return
    */
  def doTFIDFByTileFast[X](config: TFIDFConfiguration)(input: RDD[TileData[Map[String, Int], X]])
  : RDD[TileData[List[(String, Double)], X]] = {
    input.cache
    val docsWithLevel = input.flatMap(datum => datum.bins.seq.map(bin => (datum.coords._1, bin)))
    val dictionary = getDictionaries[(Int, Map[String, Int])](config.dictionaryConfig, datum => datum)(docsWithLevel)
    val docCountByLevel = docsWithLevel.map { case (level, doc) =>
      if (doc.isEmpty) {
        (level, 0)
      } else {
        (level, 1)
      }
    }.reduceByKey(_ + _).collect.toMap
    val idfs = dictionary.map { case (term, termDocsByLevel) =>
      (
        term,
        termDocsByLevel.map { case (level, documentsWithTerm) =>
          (level, config.idf.inverseDocumentFrequency(docCountByLevel(level), documentsWithTerm))
        }
        )
    }.toMap

    tileTransformationOp((binData: Map[String, Int], tile: (Int, Int, Int), bin: (Int, Int)) => {
      if (binData.isEmpty) {
        List[(String, Double)]()
      } else {
        val level = tile._1
        // Filter out terms that aren't in our dictionary
        val knownWords = binData.filter { case (term, termFrequency) =>
            idfs.contains(term)
        }

        val maxRawFrequency = knownWords.map(_._2).max
        val terms = knownWords.size

        binData.map { case (term, rawFrequency) =>
          val tf = config.tf.termFrequency(rawFrequency, terms, maxRawFrequency)
          val idf = idfs(term)(level)
          (term, tf * idf)
        }.toList.sortBy(_._2).take(config.wordsToKeep)
      }
    })(input)
  }

  /**
    * Perform LDA on the output of termFrequency, on a tile by tile basis, outputting the top topics in each tile
    *
    * This assumes a single bin per tile
    *
    * @param config The configuration for how to run LDA
    * @param input The input data of tiles of word bags
    * @tparam X The type of metadata associated with each tile
    * @return A new tile set containing the LDA results on each word bag
    */
  def ldaTopicsByTile[X] (config: LDAConfig)
                         (input: RDD[SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]]):
  RDD[SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]] = {
    type InSeries  = SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]
    type OutSeries = SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]
    val transform: InSeries => Map[String, Int] = _.bins(0)

    LDAOp.wordBagLDA(config, transform)(input).map { case (inData, ldaResults) =>
      val outputResults = ldaResults.map { t =>
        (t.topic.map { ws => ws.word + config.scoreSeparator + ws.score }.mkString(config.wordSeparator), t.score)
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
    * @param config The configuration for how to run LDA
    * @param input The input data of tiles of word bags
    * @tparam X The type of metadata associated with each tile
    * @return A new tile set containing the LDA results on each word bag
    */
  def ldaWordsByTile[X] (config: LDAConfig)
                        (input: RDD[SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]]):
  RDD[SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]] = {
    type InSeries  = SeriesData[(Int, Int, Int), (Int, Int), Map[String, Int], X]
    type OutSeries = SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], X]
    val transform: InSeries => Map[String, Int] = _.bins(0)

    LDAOp.wordBagLDA(config, transform)(input).map { case (inData, ldaResults) =>
      val wordScores = MutableMap[String, Double]()
      ldaResults.foreach { t =>
        val topicScore = t.score
        t.topic.foreach { ws =>
          wordScores(ws.word) = wordScores.getOrElse(ws.word, 0.0) + topicScore * ws.score
        }
      }
      val outputResults = wordScores.toList.sortBy(-_._2).take(config.wordsPerTopic)
      new OutSeries(
        inData.projection,
        inData.maxBin,
        inData.coords,
        SparseArray[List[(String, Double)]](1, List[(String, Double)](), 0.0f)(0 -> outputResults),
        inData.tileMeta)
    }
  }
  /**
    * Take a set of tiles, and transform the tile contents
    *
    * @param input The input data set of already tiled data
    * @tparam TC The tile coordinate type
    * @tparam BC The bin coordinate type
    * @tparam X The aggregators' coordinate type
    * @tparam VI The input value type
    * @tparam VO The output value type
    * @return A new set of tiles with the contents transformed as per the input function
    */
  def tileTransformationOp[TC, BC, X, VI, VO: ClassTag] (transformation: (VI, TC, BC) => VO)
                                                        (input: RDD[SeriesData[TC, BC, VI, X]]): RDD[SeriesData[TC, BC, VO, X]] = {
    input.map { data =>
      val tileCoordinate = data.coords
      val projection = data.projection
      val maxBin = data.maxBin

      val outputBins = data.bins.mapWithIndex { (value, index) =>
        val binCoordinate = projection.binFrom1D(index, maxBin)
        transformation(value, tileCoordinate, binCoordinate)
      }

      new SeriesData[TC, BC, VO, X](
        data.projection, maxBin, tileCoordinate, outputBins, data.tileMeta
      )
    }
  }
}

object WordCounter {
  val wordSeparators = "('$|^'|'[^a-zA-Z_0-9']+|[^a-zA-Z_0-9']+'|[^a-zA-Z_0-9'])+"
}

class WordCounter extends Aggregator[String, MutableMap[String, Int], Map[String, Int]] {
  override def default(): MutableMap[String, Int] = MutableMap[String, Int]()

  override def finish(intermediate: MutableMap[String, Int]): Map[String, Int] = intermediate.toMap

  override def merge(left: MutableMap[String, Int], right: MutableMap[String, Int]): MutableMap[String, Int] = {
    right.foreach { case (term, frequency) =>
      left(term) = left.getOrElse(term, 0) + frequency
    }
    left
  }

  override def add(current: MutableMap[String, Int], next: Option[String]): MutableMap[String, Int] = {
    next.foreach { input =>
      input.split(WordCounter.wordSeparators).map(_.toLowerCase.trim).filter(!_.isEmpty).foreach(word =>
        current(word) = current.getOrElse(word, 0) + 1
      )
    }
    current
  }
}
