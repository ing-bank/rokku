package com.ing.wbaa.rokku.proxy.handler.namespace

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import com.amazonaws.auth.BasicAWSCredentials
import com.ing.wbaa.rokku.proxy.config.{ NamespaceSettings, StorageS3Settings }
import com.ing.wbaa.rokku.proxy.data.{ RequestId, User }
import com.ing.wbaa.rokku.proxy.handler.namespace.RequestHandlerS3WithNamespacesSpec._
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext

class RequestHandlerS3WithNamespacesSpec extends AnyWordSpec with Diagrams with RequestHandlerS3WithNamespaces {

  implicit val requestId: RequestId = RequestId("test")
  implicit val system: ActorSystem = ActorSystem.create("test-system")
  override implicit val executionContext: ExecutionContext = system.dispatcher
  override protected[this] def storageS3Settings: StorageS3Settings = new StorageS3Settings(system.settings.config)
  override def addIfAllowedUserToRequestQueue(user: User)(implicit id: RequestId): Boolean = false
  override def decrement(user: User)(implicit id: RequestId): Unit = {}

  override val namespaceSettings: NamespaceSettings = new NamespaceSettings(system.settings.config) {
    override val isEnabled: Boolean = true
    override val namespaceCredentialsMap: ListMap[NamespaceName, BasicAWSCredentials] =
      ListMap(
        NamespaceName(ns1Name1) -> new BasicAWSCredentials(keyName1, secretName1),
        NamespaceName(ns1Name2) -> new BasicAWSCredentials(keyName2, secretName2),
        NamespaceName(ns1Name3) -> new BasicAWSCredentials(keyName3, secretName3),
      )
  }

  override def isBucketInNamespace(bucketName: BucketName, namespaceName: NamespaceName)(implicit id: RequestId): Boolean = false

  updateBucketCredentials(BucketName("demobucket"), new BasicAWSCredentials(keyName1, secretName1))
  updateBucketCredentials(BucketName("demobucket2"), new BasicAWSCredentials(keyName2, secretName2))

  "RequestNamespacesHandler" should {
    "get no namespace credentials for unknown bucket" in {
      val httpRequest: HttpRequest = HttpRequest(HttpMethods.GET, Uri("http://localhost:8987/unknown/ObjectName"))
      val credentials = bucketNamespaceCredentials(httpRequest)
      assert(credentials.isEmpty)
    }

    "get dedicated namespace credentials for demobucket bucket" in {
      val httpRequest: HttpRequest = HttpRequest(HttpMethods.HEAD, Uri("http://localhost:8987/demobucket/ObjectName"))
      val credentials = bucketNamespaceCredentials(httpRequest)
      assert(credentials.get.getAWSAccessKeyId.equals(keyName1))
      assert(credentials.get.getAWSSecretKey.equals(secretName1))
    }

    "get dedicated namespace credentials for demobucket2 bucket" in {
      val httpRequest: HttpRequest = HttpRequest(HttpMethods.PUT, Uri("http://localhost:8987/demobucket2/ObjectName"))
      val credentials = bucketNamespaceCredentials(httpRequest)
      assert(credentials.get.getAWSAccessKeyId.equals(keyName2))
      assert(credentials.get.getAWSSecretKey.equals(secretName2))
    }
  }
}

object RequestHandlerS3WithNamespacesSpec {
  val ns1Name1: String = "ns1"
  val ns1Name2: String = "ns2"
  val ns1Name3: String = "ns3"
  val keyName1: String = "keyName1"
  val keyName2: String = "keyName2"
  val keyName3: String = "keyName3"
  val secretName1: String = "secretName1"
  val secretName2: String = "secretName2"
  val secretName3: String = "secretName3"
}
