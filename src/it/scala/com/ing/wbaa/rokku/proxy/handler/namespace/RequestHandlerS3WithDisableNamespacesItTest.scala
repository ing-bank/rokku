package com.ing.wbaa.rokku.proxy.handler.namespace

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.ing.wbaa.rokku.proxy.RokkuS3Proxy
import com.ing.wbaa.rokku.proxy.config.{HttpSettings, KafkaSettings, NamespaceSettings, StorageS3Settings}
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.handler.{FilterRecursiveListBucketHandler, RequestHandlerS3ItTest}
import com.ing.wbaa.rokku.proxy.provider.{AuditLogProvider, MessageProviderKafka, SignatureProviderAws}
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import org.scalatest.Assertion

import scala.collection.immutable.ListMap
import scala.concurrent.Future

class RequestHandlerS3WithDisableNamespacesItTest extends RequestHandlerS3ItTest {

  /**
   * Fixture for starting and stopping a test proxy that tests can interact with.
   *
   * @param testCode Code that accepts the created sdk
   * @return Assertion
   */
  override def withS3SdkToMockProxy(testCode: AmazonS3 => Assertion): Future[Assertion] = {
    val proxy: RokkuS3Proxy = new RokkuS3Proxy with RequestHandlerS3WithNamespaces with SignatureProviderAws
      with FilterRecursiveListBucketHandler with MessageProviderKafka with AuditLogProvider with MemoryUserRequestQueue with RequestParser {
      override implicit lazy val system: ActorSystem = testSystem
      override val httpSettings: HttpSettings = rokkuHttpSettings

      override def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = true

      override val storageS3Settings: StorageS3Settings = StorageS3Settings(testSystem)
      override val kafkaSettings: KafkaSettings = KafkaSettings(testSystem)

      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] =
        Future(Some(User(UserRawJson("userId", Some(Set("group")), "accesskey", "secretkey", None))))

      override val namespaceSettings: NamespaceSettings = new NamespaceSettings(system.settings.config) {
        override val isEnabled: Boolean = false
        override val namespaceCredentialsMap: ListMap[NamespaceName, BasicAWSCredentials] =
          ListMap(
            NamespaceName("ns1Name1") -> new BasicAWSCredentials("fake", "fake"),
          )
      }
    }
    proxy.startup.map { binding =>
      try testCode(getAmazonS3(
        authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
      ))
      finally proxy.shutdown()
    }
  }
}
