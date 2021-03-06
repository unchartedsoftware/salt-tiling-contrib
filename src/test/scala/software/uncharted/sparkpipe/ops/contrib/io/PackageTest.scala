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

package software.uncharted.sparkpipe.ops.contrib.io

import java.io.File
import java.nio.file.{Files, Paths}

import org.apache.commons.io.FileUtils
import org.apache.hadoop.hbase.client._
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.TableName
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{JsonDSL, parse}
import software.uncharted.salt.core.util.SparseArray
import software.uncharted.salt.core.projection.Projection
import software.uncharted.salt.core.projection.numeric.MercatorProjection
import software.uncharted.salt.core.generation.output.SeriesData
import software.uncharted.salt.contrib.projection.XYTimeProjection
import software.uncharted.salt.contrib.spark.SparkFunSpec
import software.uncharted.sparkpipe.ops.contrib.io

// scalastyle:off magic.number
// scalastyle:off multiple.string.literals
class PackageTest extends SparkFunSpec with JsonDSL {

  private val testDir = "build/tmp/test_file_output/test_data"
  private val testLayer = "test_layer"
  private val testBucket = "uncharted-s3-client-test"
  private val extension = ".tst"

  lazy val awsAccessKey = sys.env("AWS_ACCESS_KEY")
  lazy val awsSecretKey = sys.env("AWS_SECRET_KEY")

  lazy val zookeeperQuorum = sys.env.getOrElse("HBASE_ZOOKEEPER_QUORUM", throw new Exception("hbase.zookeeper.quorum is unset"))
  lazy val zookeeperPort = sys.env.getOrElse("HBASE_ZOOKEEPER_CLIENTPORT", "2181")
  lazy val hBaseMaster = sys.env.getOrElse("HBASE_MASTER", throw new Exception("hbase.master is unset"))
  lazy val hBaseClientKeyValMaxSize = sys.env.getOrElse("HBASE_CLIENT_KEYVALUE_MAXSIZE", "0")

  private val configFile = Seq(classOf[PackageTest].getResource("/hbase-site.xml").toURI.getPath)

  val timeProjection = new XYTimeProjection(Long.MinValue, Long.MaxValue, 1, new MercatorProjection(Seq(0)))

  def genHeatmapArray(in: Double*): SparseArray[Double] = {
    SparseArray(in.length, 0.0)(in.zipWithIndex.map(_.swap):_*)
  }

  def genTopicArray[T](in: List[(String, T)]*): SparseArray[List[(String, T)]] = {
    SparseArray(in.length, List[(String, T)]())(in.zipWithIndex.map(_.swap):_*)
  }

  implicit val formats = net.liftweb.json.DefaultFormats

  describe("#writeToFile") {
    it("should create the folder directory structure if it's missing") {
      try {
        val data = sc.parallelize(Seq(((1, 2, 3), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))))
        io.writeToFile(s"${testDir}_1", testLayer, extension)(data)
        val testFile = new File(s"${testDir}_1/$testLayer/01/2")
        assertResult(true)(testFile.exists())
      } finally {
        FileUtils.deleteDirectory(new File(s"${testDir}_1"))
      }
    }

    it("should add tiles to the folder hierarchy using a path based on TMS coords ") {
      try {
        val data = sc.parallelize(Seq(
          ((2, 2, 2), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7)),
          ((2, 2, 3), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))
        ))
        io.writeToFile(s"${testDir}_2", testLayer, extension)(data)
        assertResult(true)(new File(s"${testDir}_2/$testLayer/02/2/2.tst").exists())
        assertResult(true)(new File(s"${testDir}_2/$testLayer/02/2/3.tst").exists())
      } finally {
        FileUtils.deleteDirectory(new File(s"${testDir}_2"))
      }
    }

    it("should serialize the byte array without changing it") {
      try {
        val data = sc.parallelize(Seq(((2, 2, 2), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))))
        io.writeToFile(s"${testDir}_3", testLayer, extension)(data)
        val bytes = Files.readAllBytes(Paths.get(s"${testDir}_3/$testLayer/02/2/2.tst"))
        assertResult(Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))(bytes)
      } finally {
        FileUtils.deleteDirectory(new File(s"${testDir}_3"))
      }
    }
  }

  describe("#writeToS3") {
    val testKey0 = s"$testLayer/02/2/2.bin"
    val testKey1 = s"$testLayer/02/2/3.bin"

    it("should add tiles to the s3 bucket using key names based on TMS coords", S3Test) {
      val data = sc.parallelize(Seq(
        ((2, 2, 2), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7)),
        ((2, 2, 3), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))
      ))

      io.writeToS3(awsAccessKey, awsSecretKey, testBucket, testLayer)(data).collect()

      val s3c = new S3Client(awsAccessKey, awsSecretKey)
      assert(s3c.download(testBucket,testKey0).isDefined)
      assert(s3c.download(testBucket, testKey1).isDefined)
      s3c.delete(testBucket, testKey0)
      s3c.delete(testBucket, testKey1)
    }

    it("should serialize the byte data to the s3 bucket without changing it", S3Test) {
      val data = sc.parallelize(Seq(
        ((2, 2, 2), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))
      ))

      io.writeToS3(awsAccessKey, awsSecretKey, testBucket, testLayer)(data)
      val s3c = new S3Client(awsAccessKey, awsSecretKey)
      assertResult(Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))(s3c.download(testBucket, testKey0).getOrElse(fail()))
      s3c.delete(testBucket, testKey0)
    }
  }

  describe("#writeBytesToS3") {
    val testFile = "metadata.json"
    it("should write the byte data to the s3 bucket without changing it", S3Test) {
      io.writeBytesToS3(awsAccessKey, awsSecretKey, testBucket, testLayer)(testFile, Seq(0, 1, 2, 3, 4, 5))
      val s3c = new S3Client(awsAccessKey, awsSecretKey)
      assertResult(Seq[Byte](0, 1, 2, 3, 4, 5))(s3c.download(testBucket, s"$testLayer/$testFile").getOrElse(fail()))
      s3c.delete(testBucket, s"$testLayer/$testFile")
    }
  }

  describe("#writeToHBase") {
    it("should add tiles to the HBase Table Created", HBaseTest) {
      val data = sc.parallelize(Seq(
        ((2, 2, 2), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7)),
        ((2, 2, 3), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))
      ))
      val testQualifier = Some("tileDataQuali")

      io.writeToHBase(configFile, testLayer, testQualifier)(data).collect()

      val config = HBaseConfiguration.create()
      config.set("hbase.zookeeper.quorum", zookeeperQuorum)
      config.set("hbase.zookeeper.property.clientPort", zookeeperPort)
      config.set("hbase.master", hBaseMaster)
      config.set("hbase.client.keyvalue.maxsize", hBaseClientKeyValMaxSize)
      val connection = ConnectionFactory.createConnection(config)
      val admin = connection.getAdmin
      assertResult(true)(connection.getTable(TableName.valueOf(testLayer)).exists(new Get ("02,2,2".getBytes())))
      assertResult(true)(connection.getTable(TableName.valueOf(testLayer)).exists(new Get ("02,2,3".getBytes())))
      //disable and delete test table
      admin.disableTable(TableName.valueOf(testLayer))
      admin.deleteTable(TableName.valueOf(testLayer))
      connection.close()
    }

    it("should serialize the byte data to HBase without changing it", HBaseTest) {
      val data = sc.parallelize(Seq(
        ((2, 2, 2), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7)),
        ((2, 2, 3), Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))
      ))

      val testCol = "tileData"
      val testQualifier = Some("tileDataQuali")
      io.writeToHBase(configFile, testLayer, testQualifier)(data)
      val config = HBaseConfiguration.create()
      config.set("hbase.zookeeper.quorum", zookeeperQuorum)
      config.set("hbase.zookeeper.property.clientPort", zookeeperPort)
      config.set("hbase.master", hBaseMaster)
      config.set("hbase.client.keyvalue.maxsize", hBaseClientKeyValMaxSize)
      val connection = ConnectionFactory.createConnection(config)
      val admin = connection.getAdmin

      assertResult(Seq[Byte](0, 1, 2, 3, 4, 5, 6, 7))(connection.getTable(TableName.valueOf(testLayer)).get(new Get("02,2,2".getBytes).addFamily(testCol.getBytes)).value().toSeq)

      admin.disableTable(TableName.valueOf(testLayer))
      admin.deleteTable(TableName.valueOf(testLayer))
      connection.close()
    }
  }

  describe("#writeBytesToHBase") {
    val testFile = "metadata.json"
    val testQualifier = Some("tileQualifier")
    it("should write the byte data to the HBaseTable without changing it", HBaseTest) {
      io.writeBytesToHBase(configFile, testLayer, testQualifier)(testFile, Seq(0, 1, 2, 3, 4, 5))

      //disable and delete table
      val config = HBaseConfiguration.create()
      config.set("hbase.zookeeper.quorum", zookeeperQuorum)
      config.set("hbase.zookeeper.property.clientPort", zookeeperPort)
      config.set("hbase.master", hBaseMaster)
      config.set("hbase.client.keyvalue.maxsize", hBaseClientKeyValMaxSize)
      val connection = ConnectionFactory.createConnection(config)
      val admin = connection.getAdmin

      assertResult(true)(connection.getTable(TableName.valueOf(testLayer)).exists(new Get (s"${testLayer}/${testFile}".getBytes())))
      admin.disableTable(TableName.valueOf(testLayer))
      admin.deleteTable(TableName.valueOf(testLayer))
      connection.close()

    }
  }

  describe("#serializeBinArray") {
    val arr0 = genHeatmapArray(0.0, 1.0, 2.0, 3.0)
    val arr1 = genHeatmapArray(4.0, 5.0, 6.0, 7.0)

    it("should create an RDD of bin coordinate / byte array tuples from series data") {

      val series = sc.parallelize(
        Seq(
          new SeriesData(timeProjection, (3, 1, 1), (1, 2, 3), arr0, Some(0.0, 1.0)),
          new SeriesData(timeProjection, (3, 1, 1), (4, 5, 6), arr1, Some(0.0, 1.0))
        ))
      val result = io.serializeBinArray(series).collect()
      assertResult(2)(result.length)
      assertResult((1, 2, 3))(result(0)._1)
      assertResult(
        List(
          0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, -16, 63,
          0, 0, 0, 0, 0, 0, 0, 64,
          0, 0, 0, 0, 0, 0, 8, 64))(result(0)._2)

      assertResult((4, 5, 6))(result(1)._1)
      assertResult(
        List(
          0, 0, 0, 0, 0, 0, 16, 64,
          0, 0, 0, 0, 0, 0, 20, 64,
          0, 0, 0, 0, 0, 0, 24, 64,
          0, 0, 0, 0, 0, 0, 28, 64))(result(1)._2)
    }
  }

  describe("#mkRowId") {
    it("should form proper IDs without suffix or prefix") {
      assertResult("04,07,03")(io.mkRowId("", ",", "")(4, 7, 3))
      assertResult("03:1:3")(io.mkRowId("", ":", "")(3, 1, 3))
      assertResult("10--0045--0110")(io.mkRowId("", "--", "")(10,45,110))
    }
    it("should prepend and append prefix and suffix properly") {
      assertResult("abc/04/07/03.bin")(io.mkRowId("abc/", "/", ".bin")(4, 7, 3))
      assertResult("abc/03:1:3.bin")(io.mkRowId("abc/", ":", ".bin")(3, 1, 3))
      assertResult("abc/10--0045--0110.bin")(io.mkRowId("abc/", "--", ".bin")(10,45,110))
    }
  }

  describe("#serializeBinArray") {
    it("should ignore tiles with no updated bins") {
      val series = sc.parallelize(
        Seq(new SeriesData(
          timeProjection, (3, 1, 1), (1, 2, 3), genHeatmapArray(0.0, 0.0, 0.0, 0.0), Some(0.0, 1.0))))
      val result = io.serializeBinArray(series).collect()
      assertResult(0)(result.length)
    }
  }

  // Alternative reference version against which to test.  The current implementation should be faster.
  val canonicalDoubleTileToByteArrayDense: SparseArray[Double] => Seq[Byte] = sparseData => {
    for (bin <- sparseData.seq;  i <- 0 until io.doubleBytes) yield {
      val datum = java.lang.Double.doubleToLongBits(bin)
      ((datum >> (i * io.doubleBytes)) & 0xff).asInstanceOf[Byte]
    }
  }

  describe("#doubleTileToByteDenseArray") {
    it("should be the same in version 1 and 2") {
      val data = genHeatmapArray(1.0, 2.0, 3.0, 4.0)
      val ba1 = canonicalDoubleTileToByteArrayDense(data)
      val ba2 = io.doubleTileToByteArrayDense(data)
      assertResult(ba1.length)(ba2.length)
      for (i <- ba1.indices) assertResult(ba1(i))(ba2(i))
    }
    it("should test byteArrayDenseToDoubleTile functionality") {
      val data = genHeatmapArray(1.0, 2.0, 3.0, 4.0)
      val byteSequence = io.doubleTileToByteArrayDense(data)
      val resultantArray = io.byteArrayDenseToDoubleTile(byteSequence)
      val data2 = SparseArray(resultantArray.length, 0.0)(resultantArray.zipWithIndex.map(_.swap) :_*)
      assertResult(data.length)(data2.length)
      for (i <- data.indices) assertResult(data(i))(data2(i))
    }
  }

  def getJSON (from: Array[((Int, Int, Int), Seq[Byte])], index: Int): JValue =
    parse(new String(from(index)._2.toArray))

  describe("#serializeElementScore") {

    it("should create an RDD of JSON strings serialized to bytes from series data ") {
      val arr0 = genTopicArray(List("aa" -> 1, "bb" -> 2))
      val arr1 = genTopicArray(List("cc" -> 3, "dd" -> 4))

      val series = sc.parallelize(
        Seq(
          new SeriesData(timeProjection, (1, 1, 1), (1, 2, 3), arr0, None),
          new SeriesData(timeProjection, (1, 1, 1), (4, 5, 6), arr1, None)

        ))
      val result = io.serializeElementScore(series).collect()
      assertResult(2)(result.length)

      assertResult(result(0)._1)((1, 2, 3))
      val expected0: JValue = parse("""[{"binIndex": 0, "topics": {"aa": 1, "bb": 2}}]""")
      assertResult(expected0)(getJSON(result, 0))

      assertResult(result(1)._1)((4, 5, 6))
      val expected1: JValue =  parse("""[{"binIndex": 0, "topics": {"cc": 3, "dd": 4}}]""")
      assertResult(expected1)(getJSON(result, 1))
    }

    it("should test byteArrayToIntScore deserialization function") {
      val arr0 = genTopicArray(List("aa" -> 1, "bb" -> 2))
      val arr1 = genTopicArray(List("cc" -> 3, "dd" -> 4))

      val series = sc.parallelize(
        Seq(
          new SeriesData(timeProjection, (1, 1, 1), (1, 2, 3), arr0, None),
          new SeriesData(timeProjection, (1, 1, 1), (4, 5, 6), arr1, None)
        ))

      val expected = series.filter(t => t.bins.density() > 0)
        .map { tile =>
          (tile.coords, tile.bins)
        }.collect()

      val result = io.serializeElementScore(series).collect().map {tile =>
        (tile._1, io.byteArrayToIntScoreList(tile._2))
      }
      assertResult(result(0)._1)(expected(0)._1)
      val expected0 = Map("binIndex" -> 0, "topics" -> Map("aa" -> 1, "bb" -> 2))
      val result0 = result(0)._2(0).head
      assertResult(expected0)(result0)

      assertResult(result(1)._1)(expected(1)._1)
      val expected1 =  Map("binIndex" -> 0, "topics" -> Map("cc" -> 3, "dd" -> 4))
      val result1 = result(1)._2(0).head
      assertResult(expected1)(result1)
    }

    it("Should leave the order of the list alone") {
      val list = SparseArray(4, List[(String, Int)](), 0.0f)(0 -> List(
        "rudabega" -> 1, "watermelon" -> -1, "cantaloupe" -> 2, "honeydew" -> 3, "kholrabi" -> 0,
        "broccoli" -> 12, "mustard~greens" -> 8, "rappini" -> 4, "okra" -> -23, "gai~lan" -> 15))
      val projection: Projection[_, (Int, Int, Int), (Int, Int)] = new MercatorProjection(Seq(0))
      val data = new SeriesData[(Int, Int, Int), (Int, Int), List[(String, Int)], Nothing](projection, (1, 1), (0, 0, 0), list, None)
      val series = sc.parallelize(Seq(data))
      val tile = io.serializeElementScore(series).collect
      assertResult(1)(tile.length)
      assertResult((0, 0, 0))(tile(0)._1)
      val result = new String(tile(0)._2.toArray).split("[{} :0-9,\"-]+").slice(3, 13)
      assertResult(10)(result.length)
      assertResult(List(
        "rudabega", "watermelon", "cantaloupe", "honeydew", "kholrabi",
        "broccoli", "mustard~greens", "rappini", "okra", "gai~lan"))(result.toList)
    }

    it("should ignore tiles with no updated bins ") {
      val series = sc.parallelize(
        Seq(new SeriesData[(Int, Int, Int), (Int, Int, Int), List[(String, Int)], Nothing]
        (timeProjection, (1, 1, 1), (1, 2, 3), genTopicArray(List()), None)))
      val result = io.serializeElementScore(series).collect()
      assertResult(0)(result.length)
    }
  }

  describe("#serializeElementDoubleScore") {
    it("should create an RDD of JSON strings serialized to bytes from series data ") {
      val arr0 = genTopicArray(List("aa" -> 1.0, "bb" -> 2.0))
      val arr1 = genTopicArray(List("cc" -> 3.0, "dd" -> 4.0))

      val series = sc.parallelize(
        Seq(
          new SeriesData(timeProjection, (1, 1, 1), (1, 2, 3), arr0, None),
          new SeriesData(timeProjection, (1, 1, 1), (4, 5, 6), arr1, None)
        ))

      val result = io.serializeElementDoubleScore(series).collect()
      assertResult(2)(result.length)

      assertResult(result(0)._1)((1, 2, 3))
      val expected0: JValue = Map("aa" -> 1.0, "bb" -> 2.0)
      assertResult(expected0)(getJSON(result, 0))

      assertResult(result(1)._1)((4, 5, 6))
      val expected1: JValue = Map("cc" -> 3.0, "dd" -> 4.0)
      assertResult(expected1)(getJSON(result, 1))
    }

    it("should test byteArrayToDoubleScoreList deserialization function") {
      val arr0 = genTopicArray(List("aa" -> 1.0, "bb" -> 2.0))
      val arr1 = genTopicArray(List("cc" -> 3.0, "dd" -> 4.0))

      val series = sc.parallelize(
        Seq(
          new SeriesData(timeProjection, (1, 1, 1), (1, 2, 3), arr0, None),
          new SeriesData(timeProjection, (1, 1, 1), (4, 5, 6), arr1, None)
        ))

      val expected = series.filter(t => t.bins.density() > 0)
        .map { tile =>
          (tile.coords, tile.bins)
        }.collect()

      val result = io.serializeElementDoubleScore(series).collect().map { tile =>
        (tile._1, io.byteArrayToDoubleScoreList(tile._2))
      }

      assertResult(result(0)._1)(expected(0)._1)
      val expected0 = Map("aa" -> 1, "bb" -> 2)
      val result0 = result(0)._2(0).toMap
      assertResult(expected0)(result0)

      assertResult(result(1)._1)(expected(1)._1)
      val expected1 = Map("cc" -> 3, "dd" -> 4)
      val result1 = result(1)._2(0).toMap
      assertResult(expected1)(result1)
    }

    it("Should leave the order of the list alone") {
      val list = SparseArray(4, List[(String, Double)](), 0.0f)(0 -> List(
        "rudabega" -> 1.0, "watermelon" -> -1.1, "canteloupe" -> 2.2, "honeydew" -> 3.3, "kholrabi" -> 0.4,
        "broccoli" -> 12.5, "mustard~greens" -> 8.6, "rappini" -> 4.7, "okra" -> -23.8, "gai~lan" -> 15.9))
      val projection: Projection[_, (Int, Int, Int), (Int, Int)] = new MercatorProjection(Seq(0))
      val data = new SeriesData[(Int, Int, Int), (Int, Int), List[(String, Double)], Nothing](projection, (1, 1), (0, 0, 0), list, None)
      val series = sc.parallelize(Seq(data))
      val tile = io.serializeElementDoubleScore(series).collect
      assertResult(1)(tile.length)
      assertResult((0, 0, 0))(tile(0)._1)
      val result = new String(tile(0)._2.toArray).split("[{} :0-9.,\"-]+").drop(1)
      assertResult(10)(result.length)
      assertResult(List(
        "rudabega", "watermelon", "canteloupe", "honeydew", "kholrabi",
        "broccoli", "mustard~greens", "rappini", "okra", "gai~lan"))(result.toList)
    }

    it("should ignore tiles with no updated bins ") {
      val series = sc.parallelize(
        Seq(new SeriesData[(Int, Int, Int), (Int, Int, Int), List[(String, Double)], Nothing]
          (timeProjection, (1, 1, 1), (1, 2, 3), genTopicArray(List()), None)))
      val result = io.serializeElementDoubleScore(series).collect()
      assertResult(0)(result.length)
    }
  }

}
