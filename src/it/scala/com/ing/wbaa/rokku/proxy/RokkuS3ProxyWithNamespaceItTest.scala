package com.ing.wbaa.rokku.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.stream.Materializer
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.ing.wbaa.rokku.proxy.config._
import com.ing.wbaa.rokku.proxy.data.{RequestId, S3Request, User}
import com.ing.wbaa.rokku.proxy.handler.FilterRecursiveListBucketHandler
import com.ing.wbaa.rokku.proxy.handler.namespace.{NamespaceName, RequestHandlerS3WithNamespaces}
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.provider._
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import org.scalatest.Assertion

import scala.collection.immutable.ListMap
import scala.concurrent.Future

class RokkuS3ProxyWithNamespaceItTest extends RokkuS3ProxyItTest {

  /**
    * Fixture for starting and stopping a test proxy that tests can interact with.
    *
    * @param testCode Code that accepts the created STS sdk and an authority for an S3 sdk
    * @return Future[Assertion]
    */
  override def withSdkToMockProxy(testCode: (AWSSecurityTokenService, Authority) => Future[Assertion]): Future[Assertion] = {
    val proxy: RokkuS3Proxy = new RokkuS3Proxy with RequestHandlerS3WithNamespaces
      with FilterRecursiveListBucketHandler with AuthenticationProviderSTS
      with AuthorizationProviderRanger with SignatureProviderAws
      with MessageProviderKafka with AuditLogProvider with MemoryUserRequestQueue with RequestParser {
      override implicit lazy val system: ActorSystem = testSystem
      override def materializer: Materializer = Materializer(system)
      override val httpSettings: HttpSettings = rokkuHttpSettings
      override val storageS3Settings: StorageS3Settings = StorageS3Settings(testSystem)
      override val stsSettings: StsSettings = StsSettings(testSystem)
      override val kafkaSettings: KafkaSettings = KafkaSettings(testSystem)

      override protected def rangerSettings: RangerSettings = RangerSettings(testSystem)

      override def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = {
        user match {
          case User(userName, _, _, _, _) if userName.value == "testuser" => true
          case _ => super.isUserAuthorizedForRequest(request, user)
        }
      }

      override val namespaceSettings: NamespaceSettings = new NamespaceSettings(system.settings.config) {
        override val isEnabled: Boolean = true
        override val namespaceCredentialsMap: ListMap[NamespaceName, BasicAWSCredentials] =
          ListMap(
            NamespaceName("fakeNsName1") -> new BasicAWSCredentials("fake1", "fake2"),
            NamespaceName("nsName1") -> new BasicAWSCredentials("nsAccessKeyOne", "nsSecretKeyOne"),
            NamespaceName("fakeNsName2") -> new BasicAWSCredentials("fake11", "fake11"),
            NamespaceName("nsName2") -> new BasicAWSCredentials("nsAccessKeyTwo", "nsSecretKeyTwo"),
            NamespaceName("nsName") -> new BasicAWSCredentials("accesskey", "secretkey"),
            NamespaceName("fakeNsName3") -> new BasicAWSCredentials("fake111", "fake111"),
          )
      }
    }
    proxy.startup.flatMap { binding =>
      val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
      testCode(getAmazonSTSSdk(StsSettings(testSystem).stsBaseUri), authority)
        .andThen { case _ => proxy.shutdown() }
    }
  }
}

