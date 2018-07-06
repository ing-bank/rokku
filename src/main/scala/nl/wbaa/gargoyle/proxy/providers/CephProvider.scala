package nl.wbaa.gargoyle.proxy.providers

import java.net.URI

import akka.http.scaladsl.model.HttpRequest
import com.amazonaws.{ClientConfiguration, DefaultRequest, SignableRequest}
import com.amazonaws.auth.{AWS3Signer, AWS4Signer, BasicAWSCredentials}
import com.amazonaws.http.{AmazonHttpClient, ExecutionContext, HttpMethodName}
import com.amazonaws.services.s3.internal.S3StringResponseHandler
import com.typesafe.config.ConfigFactory

/**
  * Sample S3 Provider, ceph
  *
  */
class CephProvider extends StorageProvider {
  import CephProvider._

  def verifyS3Signature() = {}

  /**
    * Translates user request and executes it using Proxy credentials
    *
    * @param request
    * @return
    */
  def translateRequest(request: HttpRequest): String = {
    val path   = request.uri.path.toString()
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
  *
  */
private class ContentHash extends AWS4Signer {
  def calculate(request: SignableRequest[_]) = super.calculateContentHash(request)
}

object CephProvider {
  private val config         = ConfigFactory.load().getConfig("s3.settings")
  private val accessKey      = config.getString("aws_access_key")
  private val secretKey      = config.getString("aws_secret_key")
  private val s3Endpoint     = config.getString("s3_endpoint")

  private val credentials = new BasicAWSCredentials(accessKey, secretKey)
  private val cephRGW     = new URI(s3Endpoint)

  private def s3Request(service: String): DefaultRequest[Nothing] = new DefaultRequest(service)

  /**
    * Prepares S3 request based on user request
    *
    * @param method
    * @param path
    * @param request
    * @param endpoint
    * @return
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
    *
    * @param request
    * @param cred
    * @param signerVer
    * @param region
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
    *
    * @param request
    * @return
    */
  private def calculateContentHash(request: DefaultRequest[_]): String = new ContentHash().calculate(request)

  /**
    * Sends request to S3 backend
    *
    * @param request
    * @return
    */
  def execS3Request(request: DefaultRequest[_]): String = {
    try {
      val clientConf = new ClientConfiguration()
      clientConf.addHeader("x-amz-content-sha256", calculateContentHash(request))

      signS3Request(request, credentials)

      val response = new AmazonHttpClient(clientConf)
        .requestExecutionBuilder()
        .executionContext(new ExecutionContext(true))
        .request(request)
        .execute(new S3StringResponseHandler()).getAwsResponse.getResult

      response
    } catch {
      case e: Exception => throw new Exception(e.getMessage)
    }
  }
}
