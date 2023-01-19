package com.ing.wbaa.rokku.proxy.handler.namespace

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.SdkClientException
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.ing.wbaa.rokku.proxy.RokkuS3Proxy
import com.ing.wbaa.rokku.proxy.config.{HttpSettings, KafkaSettings, NamespaceSettings, StorageS3Settings}
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.FilterRecursiveListBucketHandler
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.provider.{AuditLogProvider, MessageProviderKafka, SignatureProviderAws}
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import com.ing.wbaa.testkit.RokkuFixtures
import org.scalatest.Assertion
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AsyncWordSpec

import java.io.File
import scala.collection.immutable.ListMap
import scala.concurrent.Future

class RequestHandlerS3WithNamespacesItTest extends AsyncWordSpec with Diagrams with RokkuFixtures {

  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  val rokkuHttpSettings: HttpSettings = new HttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  /**
   * Fixture for starting and stopping a test proxy that tests can interact with.
   *
   * @param testCode Code that accepts the created sdk
   * @return Assertion
   */
  def withS3SdkToMockProxy(testCode: AmazonS3 => Assertion): Future[Assertion] = {
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
        override val isEnabled: Boolean = true
        override val namespaceCredentialsMap: ListMap[NamespaceName, BasicAWSCredentials] =
          ListMap(
            NamespaceName("fakeNsName1") -> new BasicAWSCredentials("fake1", "fake2"),
            NamespaceName("nsName1") -> new BasicAWSCredentials("nsAccessKeyOne", "nsSecretKeyOne"),
            NamespaceName("fakeNsName2") -> new BasicAWSCredentials("fake11", "fake11"),
            NamespaceName("nsName2") -> new BasicAWSCredentials("nsAccessKeyTwo", "nsSecretKeyTwo"),
            NamespaceName("fakeNsName3") -> new BasicAWSCredentials("fake111", "fake111"),
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


  ListMap("nsName1" -> "nsOneBucket_1", "nsName2" -> "nsTwoBucket_1")foreach { case (namespace, testBucket) =>
    s"S3 Proxy for $namespace (where $testBucket exists)" should {
      "put, get and delete an object from the bucket" in withS3SdkToMockProxy { sdk =>
        withFile(1024 * 1024) { filename =>
          val testKeyContent = "keyPutFileByContent"
          val testKeyFile = "keyPutFileByFile"
          val testContent = "content"

          // PUT
          sdk.putObject(testBucket, testKeyContent, testContent)
          sdk.putObject(testBucket, testKeyFile, new File(filename))

          // GET
          val checkContent = sdk.getObjectAsString(testBucket, testKeyContent)
          assert(checkContent == testContent)
          val keys1 = getKeysInBucket(sdk, testBucket)
          List(testKeyContent, testKeyFile).map(k => assert(keys1.contains(k)))

          // DELETE
          sdk.deleteObject(testBucket, testKeyContent)
          val keys2 = getKeysInBucket(sdk, testBucket)
          assert(!keys2.contains(testKeyContent))
          assert(keys2.contains(testKeyFile))

          sdk.deleteObject(testBucket, testKeyFile)
          val keys3 = getKeysInBucket(sdk, testBucket)
          assert(!keys3.contains(testKeyFile))
        }
      }
    }
  }

  "S3 Proxy for not existing bucket" should {
    val testBucket = "notexistingbucket"
    "throw SdkClientException exception" in withS3SdkToMockProxy { sdk =>
      assertThrows[SdkClientException] {
        sdk.listObjectsV2(testBucket)
      }
    }
  }
}
