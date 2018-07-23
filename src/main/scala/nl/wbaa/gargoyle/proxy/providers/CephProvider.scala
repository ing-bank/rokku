package nl.wbaa.gargoyle.proxy.providers

import java.net.URI

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.amazonaws.auth.{ AWS3Signer, AWS4Signer, AWSCredentials, BasicAWSCredentials }
import com.amazonaws.http.{ AmazonHttpClient, HttpMethodName, ExecutionContext => AmazonExecutionContext }
import com.amazonaws.services.s3.internal.S3StringResponseHandler
import com.amazonaws.{ ClientConfiguration, DefaultRequest, SignableRequest }
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Sample S3 Provider, ceph
 *
 */
class CephProvider extends StorageProvider {
  import CephProvider._

  def endpoint: String = s3Endpoint
  def credentials: AWSCredentials = s3credentials

  def verifyS3Signature(): Unit = {}

  /**
   * Translates user request and executes it using Proxy credentials
   */
  def translateRequest(request: HttpRequest): String = {
    val path = request.uri.path.toString()
    val method = request.method.value match {
      case "GET"    => HttpMethodName.GET
      case "POST"   => HttpMethodName.POST
      case "PUT"    => HttpMethodName.PUT
      case "DELETE" => HttpMethodName.DELETE
    }

    val proxyRequest = generateS3Request(method, path)
    execS3Request(proxyRequest)
  }
}

/**
 * Sample S3 request generation methods, based on AWS Signer class
 * requires admin user AWS credentials
 */
private class ContentHash extends AWS4Signer {
  def calculate(request: SignableRequest[_]): String = super.calculateContentHash(request)
}

object CephProvider {
  private val config = ConfigFactory.load().getConfig("s3.settings")
  private val accessKey = config.getString("aws_access_key")
  private val secretKey = config.getString("aws_secret_key")
  val s3Endpoint: String = config.getString("s3_endpoint")

  val s3credentials = new BasicAWSCredentials(accessKey, secretKey)
  private val cephRGW = new URI(s3Endpoint)

  private def s3Request(service: String): DefaultRequest[Nothing] = new DefaultRequest(service)

  /**
   * Prepares S3 request based on user request
   */
  def generateS3Request(method: HttpMethodName, path: String, request: DefaultRequest[_] = s3Request("s3"), endpoint: URI = cephRGW): DefaultRequest[_] = {
    request.setHttpMethod(method)
    request.setEndpoint(endpoint)
    request.setResourcePath(path)
    //todo: add user request parameters
    request
  }

  /**
   * Signs S3 request with provided credentials. During sign AWS specific headers are added to request
   */
  private def signS3Request(request: DefaultRequest[_], cred: BasicAWSCredentials, signerVer: String = "v4", region: String = "us-east-1"): Unit = {
    signerVer match {
      case "v3" =>
        val singer = new AWS3Signer()
        singer.sign(request, cred)

      case "v4" =>
        val signer = new AWS4Signer()
        signer.setRegionName(region)
        signer.setServiceName(request.getServiceName)
        signer.sign(request, cred)
    }
  }

  /**
   * aws requires x-amz-content-sha256 even for UNSIGNED_PAYLOAD
   */
  private def calculateContentHash(request: DefaultRequest[_]): String = new ContentHash().calculate(request)

  /**
   * Sends request to S3 backend
   */
  def execS3Request(request: DefaultRequest[_]): String = {
    try {
      val clientConf = new ClientConfiguration()
      clientConf.addHeader("x-amz-content-sha256", calculateContentHash(request))

      signS3Request(request, s3credentials)

      val response = new AmazonHttpClient(clientConf)
        .requestExecutionBuilder()
        .executionContext(new AmazonExecutionContext(true))
        .request(request)
        .execute(new S3StringResponseHandler()).getAwsResponse.getResult

      response
    } catch {
      case e: Exception => throw new Exception(e.getMessage)
    }
  }

  // request by Akka HTTP change
  //
  def akkaS3Request(request: HttpRequest)(implicit system: ActorSystem): Future[Source[ByteString, Any]] = {
    implicit val ex: ExecutionContext = system.dispatcher

    Http().singleRequest(request).map(_.entity.dataBytes)
  }
}
