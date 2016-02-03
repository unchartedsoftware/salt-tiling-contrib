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

import java.io.{ByteArrayOutputStream, ByteArrayInputStream, InputStream}
import java.util.zip.{GZIPOutputStream, GZIPInputStream}

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{PutObjectRequest, CannedAccessControlList, ObjectMetadata, Permission, AccessControlList, GroupGrantee}
import grizzled.slf4j.Logging

object S3Client {
  def apply(accessKey: String, secretKey: String): S3Client = new S3Client(accessKey, secretKey)
}

class S3Client(accessKey: String, secretKey: String) extends Logging {

  val s3Client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey))

  def createBucket(bucketName: String): Boolean = {
    try {
      // TODO: Make CORS support an argument, enable static web serving
      if (!s3Client.doesBucketExist(bucketName)) { s3Client.createBucket(bucketName); true } else false
    } catch {
      case e: Exception => error(s"Failed to create bucket $bucketName"); false // scalastyle:ignore
    }
  }

  def deleteBucket(bucketName: String): Boolean = {
    try {
      if (s3Client.doesBucketExist(bucketName)) { s3Client.deleteBucket(bucketName); true } else false
    } catch {
      case e: Exception => error(s"Failed to delete bucket $bucketName"); false // scalastyle:ignore
    }
  }

  def delete(bucketName: String, key: String): Boolean = {
    try {
      s3Client.deleteObject(bucketName, key); true
    } catch {
      case e: Exception => error(s"Failed to delete $key", e); false
    }
  }

  def upload(data: Seq[Byte], bucketName: String, key: String, contentType: String): Boolean = {
    try {
      val compressedData = zipData(data.toArray)
      val is = new ByteArrayInputStream(compressedData.get)
      val meta = new ObjectMetadata()

      meta.setContentType(contentType)
      meta.setContentEncoding("gzip")

      s3Client.putObject(new PutObjectRequest(bucketName, key, is, meta) // scalastyle:ignore
        .withCannedAcl(CannedAccessControlList.PublicRead))
      true
    } catch {
      case e: Exception => error(s"Failed to upload $key", e); false
    }
  }

  def download(bucketName: String, key: String): Option[Seq[Byte]]= {
    try {
      val dis = s3Client.getObject(bucketName, key).getObjectContent
      val decompressedData = unzipData(dis)
      Some(decompressedData.get.toSeq)
    } catch {
      case e: Exception => error(s"Failed to download $key", e); None
    }
  }

  def zipData (dataToBeCompressed: Array[Byte]): Option[Array[Byte]] = {
    try {
      //create outputstream to write data in and the filter that would compress the data going in
      val bos = new ByteArrayOutputStream(dataToBeCompressed.length)
      val GzipFilter = new GZIPOutputStream(bos)

      //write to outputstream using the filter
      GzipFilter.write(dataToBeCompressed, 0, dataToBeCompressed.length)
      GzipFilter.flush()
      GzipFilter.close()
      Some(bos.toByteArray())
    } catch {
      case e: Exception => error("Failed to compress file", e); None
    }
  }

  def unzipData (compressedInputStream: InputStream): Option[Array[Byte]] = {
      try {
        //add the input stream into the filter GZIP input stream
        val GUnzipFilter = new GZIPInputStream(compressedInputStream)

        //create a ByteArrayOutputStream object to write the decompressed data into; convert this to an array of bytes after.
        val bos = new ByteArrayOutputStream()

        val bufferSize = 8192
        val buffer = Array.ofDim[Byte](bufferSize)

        var bytesRead =  GUnzipFilter.read(buffer, 0, bufferSize)
        while (bytesRead != -1) {
          bos.write(buffer, 0, bytesRead)
          bytesRead = GUnzipFilter.read(buffer)
        }

        GUnzipFilter.close()
        bos.close()

        //return the decompressed byte array
        Some(bos.toByteArray())
      } catch {
        case e: Exception => error("Failed to decompress file", e); None
      }
  }

}
