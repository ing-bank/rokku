package com.ing.wbaa.rokku.proxy.handler

import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.http.scaladsl.model.{HttpRequest, RemoteAddress}
import com.amazonaws.auth.SignerFactory
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{CopyObjectRequest, ObjectMetadata}
import com.ing.wbaa.rokku.proxy.RokkuS3Proxy
import com.ing.wbaa.rokku.proxy.config.{HttpSettings, KafkaSettings, StorageS3Settings}
import com.ing.wbaa.rokku.proxy.data._
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.provider.{AuditLogProvider, MessageProviderKafka, SignatureProviderAws}
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import com.ing.wbaa.testkit.RokkuFixtures
import org.scalatest._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class RequestHandlerS3ItTest extends AsyncWordSpec with DiagrammedAssertions with RokkuFixtures {
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
    val proxy: RokkuS3Proxy = new RokkuS3Proxy with RequestHandlerS3 with SignatureProviderAws
      with FilterRecursiveListBucketHandler with MessageProviderKafka with AuditLogProvider with MemoryUserRequestQueue with RequestParser {
      override implicit lazy val system: ActorSystem = testSystem
      override val httpSettings: HttpSettings = rokkuHttpSettings

      override def isUserAuthorizedForRequest(request: S3Request, user: User)(implicit id: RequestId): Boolean = true

      override val storageS3Settings: StorageS3Settings = StorageS3Settings(testSystem)
      override val kafkaSettings: KafkaSettings = KafkaSettings(testSystem)

      override def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] =
        Future(Some(User(UserRawJson("userId", Set("group"), "accesskey", "secretkey"))))

      def createLineageFromRequest(httpRequest: HttpRequest, userSTS: User, clientIPAddress: RemoteAddress)(implicit id: RequestId): Future[Done] = Future.successful(Done)

      override val requestPersistenceEnabled: Boolean = false
      override val configuredPersistenceId: String = "localhost-1"
    }
    proxy.startup.map { binding =>
      try testCode(getAmazonS3(
        authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
      ))
      finally proxy.shutdown()
    }
  }

  val awsSignerType = SignerFactory.VERSION_FOUR_SIGNER

  "S3 Proxy" should {
    s"proxy with $awsSignerType" that {

      "list the current buckets" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          assert(sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
        }
      }

      "create and remove a bucket" in withS3SdkToMockProxy { sdk =>
        val testBucket = "createbuckettest"
        sdk.createBucket(testBucket)
        assert(sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
        sdk.deleteBucket(testBucket)
        assert(!sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
      }

      "list files in a bucket" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          val testKey = "keyListFiles"

          sdk.putObject(testBucket, testKey, "content")
          val resultV2 = sdk.listObjectsV2(testBucket).getObjectSummaries.asScala.toList.map(_.getKey)
          val result = sdk.listObjects(testBucket).getObjectSummaries.asScala.toList.map(_.getKey)

          assert(resultV2.contains(testKey))
          assert(result.contains(testKey))
        }
      }

      "check if bucket exists" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          assert(sdk.doesBucketExistV2(testBucket))
        }
      }

      "head on bucket object" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          withFile(1024 * 1024) { filename =>
            val testKeyFile = "keyPutFileByFile"

            sdk.putObject(testBucket, testKeyFile, new File(filename))

            assert(sdk.doesObjectExist(testBucket, testKeyFile))
          }
        }
      }


      "put, get and delete an object from a bucket" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
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
          }
        }
      }

      "copy object with REPLACE metadata on object" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          withFile(1024 * 1024) { filename =>
            val testKeyFile = "keyPutFileByFile"
            val destinationKey = "sdkcopy"
            val ownerValue = "itTest"
            sdk.putObject(testBucket, testKeyFile, new File(filename))

            val copyRequest = new CopyObjectRequest(testBucket, testKeyFile, testBucket, destinationKey)
            val newMetadata = new ObjectMetadata()
            newMetadata.addUserMetadata("Owner", ownerValue)
            copyRequest.setMetadataDirective("REPLACE")
            copyRequest.setNewObjectMetadata(newMetadata)

            sdk.copyObject(copyRequest)
            assert(sdk.getObjectMetadata(testBucket, destinationKey).getUserMetadata.containsValue(ownerValue))
          }
        }
      }

      "put and list objects with special characters in object names" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          withFile(1024) { filename =>
            val unsafeNames = List("keywith$.txt", "keywith#.txt", "keywith!.txt", "keywith space.txt", "keywith[bracket].txt", "keywith+.txt", "keywith(curly).txt", "keywith:.txt")

            val uploadResults = unsafeNames.map { uName =>
              val u = sdk.putObject(testBucket, uName, new File(filename))
              val objectUploadedCorrectly = sdk.doesObjectExist(testBucket, uName) && !u.getETag.isEmpty
              val objectListedUsingPrefix =
                if (uName.contains(" ")) true // we skip listing with prefix containing space as AWS replaces it as "+"
                else {
                  sdk.listObjects(testBucket, uName).getObjectSummaries.get(0).getKey == uName
                }
              objectUploadedCorrectly && objectListedUsingPrefix
            }
            assert(!uploadResults.contains(false))
          }
        }
      }

      // TODO: Fix proxy for copyObject function
      //        "check if object can be copied" in withS3SdkToMockProxy(awsSignerType) { sdk =>
      //          withBucket(sdk) { testBucket =>
      //            withBucket(sdk) { tragetBucket =>
      //              withFile(1024 * 1024) { filename =>
      //                sdk.putObject(testBucket, "keyCopyOrg", new File(filename))
      //                sdk.copyObject(testBucket, "keyCopyOrg", tragetBucket, "keyCopyDest")
      //
      //                val keys1 = getKeysInBucket(sdk, testBucket)
      //                assert(!keys1.contains("keyCopyOrg"))
      //                val keys2 = getKeysInBucket(sdk, "newbucket")
      //                assert(keys2.contains("keyCopyDest"))
      //              }
      //            }
      //          }
      //      }

      "put a 1MB file in a bucket (multi part upload)" in withS3SdkToMockProxy { sdk =>
        withBucket(sdk) { testBucket =>
          withFile(1024 * 1024) { filename =>
            val testKey = "keyMultiPart1MB"
            doMultiPartUpload(sdk, testBucket, filename, testKey)
            val objectKeys = getKeysInBucket(sdk, testBucket)
            assert(objectKeys.contains(testKey))
          }
        }
      }
    }
  }
}
