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

package software.uncharted.sparkpipe.ops.xdata.io

//for logging, aperture tiles uses org.apache.log4j for logging
import grizzled.slf4j.Logging

//import needed HBase libraries
import java.net.URI

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Put}
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapred.TableOutputFormat
import org.apache.hadoop.hbase.{HBaseConfiguration, HColumnDescriptor, HTableDescriptor, TableName}
import org.apache.hadoop.mapred.JobConf
import org.apache.spark.rdd.RDD

object HBaseConnector {
  def apply(configFile: Seq[String]) : HBaseConnector = {
    new HBaseConnector(configFile)
  }
}

class HBaseConnector(configFiles: Seq[String]) extends Logging {
  private val hbaseConfiguration = HBaseConfiguration.create()
  configFiles.foreach{configFile =>
  hbaseConfiguration.addResource(new Path(new URI(configFile)))
  }
  private val connection = getConnection(hbaseConfiguration)
  private val admin = connection.getAdmin

  private val colFamilyData = "tileData"
  private val colFamilyMetaData = "tileMetaData"
  /**
   *Write Tile Data into a Table
   *
   *RDD Layer Data is stored in their own table with tile info stored in a column and tile name as the rowID
   * @param tableName name of the table to be written to
   * @param qualifierName qualifier column to be written to, if passed in
   * @param listOfRowInfo RDD tuple of String and a Sequence of Bytes; Row ID and data to be stored in table
   */
  def writeTileData(tableName: String, qualifierName: String = "")(listOfRowInfo: RDD[(String, Seq[Byte])]): Boolean = {
    writeRows(tableName = tableName, colFamilyName = colFamilyData, qualifierName = qualifierName)(listOfRowInfo)
  }
  /**
   *Write Meta Data into a Table
   *
   *RDD Layer Data is stored in their own table with tile info stored in a column and tile name as the rowID
   * @param tableName name of the table to be written to
   * @param qualifierName qualifier column to be written to, if passed in
   * @param rowID row ID where data is to be inserted into
   * @param data data to be inserted
   */
  def writeMetaData(tableName: String, qualifierName: String = "", rowID: String, data: Seq[Byte]): Boolean = {
    writeRow(tableName = tableName, colName = colFamilyMetaData, qualifierName = qualifierName, rowID = rowID, data = data)
  }

  private def writeRows(tableName: String, colFamilyName: String, qualifierName: String)(listOfRowInfo: RDD[(String, Seq[Byte])]): Boolean = {
    createTableIfNecessary(tableName)
    try {
      val putList = listOfRowInfo.map { rowInfo =>
        (new ImmutableBytesWritable, new Put(rowInfo._1.getBytes).addColumn(colFamilyName.getBytes, qualifierName.getBytes, rowInfo._2.toArray))
      }
      val jobConfig = new JobConf(hbaseConfiguration, this.getClass)
      jobConfig.setOutputFormat(classOf[TableOutputFormat])
      jobConfig.set(TableOutputFormat.OUTPUT_TABLE, tableName)
      putList.saveAsHadoopDataset(jobConfig)
      true
    } catch {
      case e: Exception => error(s"Failed to write into table $tableName"); false
    }
  }

  private def writeRow(tableName: String, colName: String, qualifierName: String, rowID: String, data: Seq[Byte]): Boolean = {
    try {
      createTableIfNecessary(tableName)
      val table = connection.getTable(TableName.valueOf(tableName))
      table.put(new Put(rowID.getBytes).addColumn(colName.getBytes, qualifierName.getBytes, data.toArray))
      table.close
      true
    } catch {
      case e: Exception => error(s"Failed to write row into table"); false
    }
  }

  def close: Unit = admin.close()

  private def createTableIfNecessary(tableName: String): Boolean = {
    try {
      if(!admin.tableExists(TableName.valueOf(tableName))) {
        createTable(tableName)
      }
      else{
        true
      }
    } catch {
      case e: Exception => error(s"couldn't return table: $tableName"); false
    }
  }

  private def createTable(tableName: String): Boolean = {
    try {
      val tableDescriptor = new HTableDescriptor(TableName.valueOf(tableName))
      tableDescriptor.addFamily(new HColumnDescriptor(colFamilyData))
      tableDescriptor.addFamily(new HColumnDescriptor(colFamilyMetaData))
      admin.createTable(tableDescriptor)
      true
    } catch {
      case e: Exception => error(s"Error while creating table"); false
    }
  }

  private def getConnection(hbaseConfiguration: Configuration): Connection = {
    ConnectionFactory.createConnection(hbaseConfiguration)
  }
}
