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
package software.uncharted.xdata.ops.io

import org.apache.spark.rdd.RDD
import scala.util.Try

/**
  * Generic I/O client that handles the general bookkeeping associated with tile sets, reducing the work of writing an
  * I/O client to the specific portions relevant to the specific format.
  *
  * @tparam T The type of information passed from the prepare function, through the write function, to the finalize function
  */
trait LocalIOClient[T] {
  /**
    * Write out an entire dataset
    *
    * @param indexFcn A function to translate from indices to differentiating i/o keys
    * @param dataSet  A set of data to write
    * @tparam I The type of index used to differentiate between data
    */
  def write[I](datasetName: String, dataSet: RDD[(I, Array[Byte])], indexFcn: (I) => String): Unit = {
    assert("local" == dataSet.context.master)
    val setInfo = prepare(datasetName)
    val localWriteRaw = writeRaw

    dataSet.foreach { case (key, data) =>
      localWriteRaw(setInfo, indexFcn(key), data)
    }

    finalize(setInfo)
  }

  /**
    * Prepare a dataset for writing
    *
    * @param datasetName The name of the dataset to write
    * @return Type of information used in write and finalize function
    */
  def prepare(datasetName: String): T

  /**
    * Write out raw data
    */
  val writeRaw: (T, String, Array[Byte]) => Unit

  /**
    * Read raw data
    */
  val readRaw: String => Try[Array[Byte]]

  /**
    * Perform any finishing actions that must be performed when writing a dataset.
    *
    * @param datasetInfo Any information that might be needed about the dataset.
    */
  def finalize(datasetInfo: T): Unit
}
