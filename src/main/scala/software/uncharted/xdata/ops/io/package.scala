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
package software.uncharted.xdata.ops

import java.io.{BufferedOutputStream, FileOutputStream, File}

import grizzled.slf4j.Logging
import org.apache.spark.rdd.RDD
import software.uncharted.salt.core.generation.output.SeriesData

import scala.util.parsing.json.JSONObject

package object io extends Logging {

  val doubleBytes = 8

  /**
   * Write binary array data to the file system.  Folder structure is
   * baseFilePath/layerName/level/xIdx/yIdx.bin.  Indexing is TMS style
   * with (0,0) in lower left, y increasing as it moves north.
   *
   * @param baseFilePath Baseline output directory - can store multiple layers.
   * @param layerName Unique name for the layer .
   * @param input tile coordinate / byte array tuples of tile data
   * @return input data unchanged
   */
  def writeToFile(baseFilePath: String, layerName: String, extension: String)(input: RDD[((Int, Int, Int), Seq[Byte])]): RDD[((Int, Int, Int), Seq[Byte])] = {
    input.collect().foreach { tileData =>
      val coord = tileData._1
      val dirPath = s"$baseFilePath/$layerName/${coord._1}/${coord._2}" //scalastyle:ignore
      val path = s"$dirPath/${coord._3}.$extension"
      try {
        // create path if necessary
        val file = new File(dirPath)
        file.mkdirs()
        val fos = new FileOutputStream(new File(path))
        val bos = new BufferedOutputStream(fos)
        bos.write(tileData._2.toArray)
        bos.close()
      } catch {
        case e: Exception => error(s"Failed to write file $path", e)
      }
    }
    input
  }

  /**
   * Write binary array data to the file system.  Folder structure is
   * baseFilePath/filename.ext.
   *
   * @param baseFilePath Baseline output directory - can store multiple layer subdirectories.
   * @param layerName Unique name for the layer.
   * @param fileName Filename to write byte data into.
   * @param bytes byte array of data
   */
  def writeBytesToFile(baseFilePath: String, layerName: String)(fileName: String, bytes: Seq[Byte]): Unit = {
    val dirPath = s"$baseFilePath/$layerName"
    val path = s"$dirPath/$fileName"
    try {
      // create path if necessary
      val file = new File(dirPath)
      file.mkdirs()
      val fos = new FileOutputStream(new File(path))
      val bos = new BufferedOutputStream(fos)
      bos.write(bytes.toArray)
      bos.close()
    } catch {
      case e: Exception => error(s"Failed to write file $path", e)
    }
  }

  /**
   * Write binary array data to Amazon S3 bucket.  Key format is layerName/level/xIdx/yIdx.bin.
   * Indexing is TMS style with (0,0) in lower left, y increasing as it moves north
   *
   * @param accessKey AWS Access key
   * @param secretKey AWS Secret key
   * @param bucketName Name of S3 bucket to write to - will create a bucket if missing
   * @param layerName Unique name for the layer
   * @param input tile coordinate / byte array tuples of tile data
   * @return input data unchanged
   */
  def writeToS3(accessKey: String, secretKey: String, bucketName: String, layerName: String)(input: RDD[((Int, Int, Int), Seq[Byte])]):
  RDD[((Int, Int, Int), Seq[Byte])] = {
    // Upload tiles to S3 using the supplied bucket and layer.  Use foreachPartition to avoid incurring
    // the cost of initializing the S3Client per record.  This can't be done outside the RDD closure
    // because the Amazon S3 API classes are not marked serializable.
    input.foreachPartition { tileDataIter =>
      val s3Client = S3Client(accessKey, secretKey)
      tileDataIter.foreach { tileData =>
        val coord = tileData._1
        // store tile in bucket as layerName/level-xIdx-yIdx.bin
        val key = s"$layerName/${coord._1}/${coord._2}/${coord._3}.bin"
        s3Client.upload(tileData._2.toArray, bucketName, key)
      }
    }
    input
  }

  /**
   * Write binary array data to Amazon S3 bucket.  Key format is layerName/filename.ext.
   *
   * @param accessKey AWS Access key
   * @param secretKey AWS Secret key
   * @param bucketName Name of S3 bucket to write to - will create a bucket if missing
   * @param layerName Unique name for the layer
   * @param bytes byte array of data to write
   */
  def writeBytesToS3(accessKey: String, secretKey: String, bucketName: String, layerName: String)(fileName: String, bytes: Seq[Byte]): Unit = {
    val s3Client = S3Client(accessKey, secretKey)
    s3Client.upload(bytes, bucketName, layerName + "/" + fileName)
  }
  /**
   *Write binary array data to HBase Table as a batch instead of row by row.
   *This is done so that you don't have to check if the table exists before you write
   *every record.
   *Other alternative is the method above. Where you check for table first.
   *With the other method you instantiate the connector one more time just to check a table
   *Write binary array data to HBase Table
   *
   *RDD Layer Data is stored in their own table with tile info stored in a column and tile name as the rowID
   * This Data is first mapped to a list of tuples containing required data to store into HBase table
   * Then the list of tuples are sent to HBase connector to batch put the list into the table
   * @param zookeeperQuorum HBase Connection Parameter
   * @param zookeeperPort HBase Connection Parameter
   * @param hBaseMaster HBase Connection Parameter
   * @param colName name of column where data will be updated
   * @param layerName unique name for the layer, will be used as table name
   * @param input RDD of tile data to be processed and stored into HBase
   */
  def writeToHBase(zookeeperQuorum: String, zookeeperPort: String, hBaseMaster: String, layerName: String, colName: String)(input: RDD[((Int, Int, Int), Seq[Byte])]): RDD[((Int, Int, Int), Seq[Byte])] = {

    input.foreachPartition { tileDataIter =>
      val hBaseConnector = HBaseConnector(zookeeperQuorum, zooKeeperPort, hBaseMaster, layerName, colName)
      val pullRowData = tileDataIter.map { tileData =>
        val coord = tileData._1
        // store tile in bucket as layerName/level-xIdx-yIdx.bin
        val fileName = s"$layerName/${coord._1}/${coord._2}/${coord._3}.bin"
        (fileName, tileData._2.toArray)
      }
      hBaseConnector.insertRows(layerName, colName, pullRowData)
      hBaseConnector.close()
    }
    input
  }

  /**
   *Write binary array data to HBase Table
   *
   *RDD Layer Data is stored in their own table with tile info stored in a column and tile name as the rowID
   * @param zookeeperQuorum HBase Connection Parameter
   * @param zookeeperPort HBase Connection Parameter
   * @param hBaseMaster HBase Connection Parameter
   * @param colName name of column where data will be inserted into
   * @param layerName unique name for the layer, will be used as table name
   * @param input RDD of tile data to be processed and stored into HBase
   */
  def writeBytesToHBase(zookeeperQuorum: String, zookeeperPort: String, hBaseMaster: String, colName: String, layerName: String)(fileName: String, bytes: Seq[Byte]): Unit = {
    val hBaseConnector = HBaseConnector(zookeeperQuorum, zookeeperPort, hBaseMaster, layerName, colName)
    hBaseConnector.insertRow(layerName, colName, (layerName + "/" + fileName), bytes)
    hBaseConnector.close()
  }

  /**
   * Serializes tile bins stored as a double array to tile index / byte sequence tuples.
   * @param tiles The input tile set.
   * @return Index/byte tuples.
   */
  def serializeBinArray(tiles: RDD[SeriesData[(Int, Int, Int), Double, (Double, Double)]]): RDD[((Int, Int, Int), Seq[Byte])] = {
    tiles.filter(t => t.binsTouched > 0)
      .map { tile =>
        val data = for (bin <- tile.bins; i <- 0 until doubleBytes) yield {
          val data = java.lang.Double.doubleToLongBits(bin)
          ((data >> (i * doubleBytes)) & 0xff).asInstanceOf[Byte]
        }
        (tile.coords, data)
      }
  }

  /**
   * Serializes tile bins stored as a double array to tile index / byte sequence tuples.
   * @param tiles The input tile set.
   * @return Index/byte tuples.
   */
  def serializeElementScore(tiles: RDD[SeriesData[(Int, Int, Int), List[(String, Int)], Nothing]]): RDD[((Int, Int, Int), Seq[Byte])] = {
    tiles.filter(t => t.binsTouched > 0)
      .map { t =>
        val bytes = new JSONObject(t.bins.head.toMap).toString().getBytes
        (t.coords, bytes.toSeq)
      }
  }
}
