package commands

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.{Region, Regions, ServiceAbbreviations}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{GetObjectRequest, ObjectMetadata, PutObjectRequest}
import com.amazonaws.util.StringInputStream

import scala.io.Source


object S3 {

  private val endpoint = Region.getRegion(Regions.fromName(Regions.EU_WEST_1.getName)).getServiceEndpoint(ServiceAbbreviations.S3)

  lazy val client: AmazonS3Client = {
    val s3 = new AmazonS3Client(goo.Config.awsUserCredentials)
    s3.setEndpoint(endpoint)
    s3
  }

  def put(bucket: String, path: String, contents: String): Unit = {
    val metaData = new ObjectMetadata()
    metaData.setContentType("text/plain")
    val request = new PutObjectRequest(bucket, path, new StringInputStream(contents), metaData)
    client.putObject(request)
  }

  def get(bucket: String, path: String): Option[String] = {
    val request = new GetObjectRequest(bucket, path)
    try {
      val s3obj = client.getObject(request)
      val contents = Source.fromInputStream(s3obj.getObjectContent, "UTF-8").getLines().mkString("\n")
      s3obj.close()
      Some(contents)
    } catch {
      case e: AmazonServiceException if e.getErrorCode == "NoSuchKey" => None
    }
  }

  def delete(bucket: String, path: String): Unit = {
    client.deleteObject(bucket, path)
  }
}
