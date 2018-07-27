package com.ing.wbaa.gargoyle.proxy

import java.io.{File, RandomAccessFile}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.services.s3.AmazonS3
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import com.ing.wbaa.gargoyle.proxy.config.{GargoyleHttpSettings, GargoyleStorageS3Settings}
import com.ing.wbaa.gargoyle.proxy.data.S3Request
import com.ing.wbaa.testkit.docker.DockerCephS3Service
import com.ing.wbaa.testkit.s3sdk.S3SdkHelpers
import org.scalatest._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class S3ProxyItTest extends AsyncWordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerCephS3Service
  with S3SdkHelpers {
  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  private[this] val gargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  // Settings to connect to S3 storage, we have to wait for the docker container to retrieve the exposed port
  private[this] lazy val gargoyleStorageS3SettingsFuture: Future[GargoyleStorageS3Settings] =
    cephContainer.getPorts()(docker = dockerExecutor, ec = dockerExecutionContext)
      .map { portMapping =>
        println(portMapping)
        new GargoyleStorageS3Settings(testSystem.settings.config) {
          override val storageS3Host: String = "127.0.0.1"
          override val storageS3Port: Int = portMapping(cephInternalPort)
        }
      }(executionContext)

  /**
    * Fixture for starting and stopping a test proxy that tests can interact with.
    *
    * @param awsSignerType Signer type for aws sdk to use
    * @param testCode      Code that accepts the created sdk
    * @return Assertion
    */
  def withSdkToTestProxy(awsSignerType: String)(testCode: AmazonS3 => Assertion): Future[Assertion] = {
    gargoyleStorageS3SettingsFuture
      .map(gargoyleStorageS3Settings =>
        new GargoyleS3Proxy {
          override implicit lazy val system: ActorSystem = testSystem
          override val httpSettings: GargoyleHttpSettings = gargoyleHttpSettings
          override def isAuthorized(request: S3Request): Boolean = true
          override val storageS3Settings: GargoyleStorageS3Settings = gargoyleStorageS3Settings
        })(executionContext)
      .map(proxy => (proxy, proxy.bind))(executionContext)
      .flatMap { proxyBind =>
        proxyBind._2.map { binding =>
          val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
          try {
            testCode(getAmazonS3(awsSignerType, authority))
          } finally {
            proxyBind._1.shutdown()
          }
        }(executionContext)
      }(executionContext)
  }

  /**
    * Fixture to create a test file with a certain size for your testcase
    *
    * @param size     Size in Bytes of the file you want to test
    * @param testCode Code that accepts the name of the created file
    * @return Assertion
    */
  def withFile(size: Long)(testCode: String => Assertion): Assertion = {
    val filename = "testfile.tst"
    val file = new RandomAccessFile(filename, "rw")
    file.setLength(size)
    file.close()

    try {
      testCode(filename)
    } finally {
      new File(filename).delete()
    }
  }

  // TODO: Expand with different signer types
  val awsSignerTypes = List(
    "S3SignerType" //,
    //      SignerFactory.NO_OP_SIGNER,
    //      SignerFactory.QUERY_STRING_SIGNER,
    //      SignerFactory.VERSION_FOUR_SIGNER,
    //      SignerFactory.VERSION_FOUR_UNSIGNED_PAYLOAD_SIGNER,
    //      SignerFactory.VERSION_THREE_SIGNER
  )

  awsSignerTypes.foreach { awsSignerType =>

    "S3 Proxy" should {
      s"proxy with $awsSignerType" that {

        "list the current buckets" in withSdkToTestProxy(awsSignerType) { sdk =>
          assert(sdk.listBuckets().asScala.toList.map(_.getName) == List("demobucket"))
        }

        "create and remove a bucket" in withSdkToTestProxy(awsSignerType) { sdk =>
          sdk.createBucket("createbuckettest")
          assert(sdk.listBuckets().asScala.toList.map(_.getName).contains("createbuckettest"))
          sdk.deleteBucket("createbuckettest")
          assert(!sdk.listBuckets().asScala.toList.map(_.getName).contains("createbuckettest"))
        }

        "list files in a bucket" in withSdkToTestProxy(awsSignerType) { sdk =>
          sdk.putObject("demobucket", "keyListFiles", "content")
          val resultV2 = sdk.listObjectsV2("demobucket").getObjectSummaries.asScala.toList.map(_.getKey)
          val result = sdk.listObjects("demobucket").getObjectSummaries.asScala.toList.map(_.getKey)

          assert(resultV2.contains("keyListFiles"))
          assert(result.contains("keyListFiles"))
        }

        "check if bucket exists" in withSdkToTestProxy(awsSignerType) { sdk =>
          assert(sdk.doesBucketExistV2("demobucket"))
        }

        "put, get and delete an object from a bucket" in withSdkToTestProxy(awsSignerType) { sdk =>
          withFile(1024 * 1024) { filename =>
            // PUT
            sdk.putObject("demobucket", "keyPutFileByContent", "content")
            sdk.putObject("demobucket", "keyPutFileByFile", new File(filename))

            // GET
            val checkContent = sdk.getObjectAsString("demobucket", "keyPutFileByContent")
            assert(checkContent == "content")
            val keys1 = getKeysInBucket(sdk)
            List("keyPutFileByContent", "keyPutFileByFile").map(k => assert(keys1.contains(k)))

            // DELETE
            sdk.deleteObject("demobucket", "keyPutFileByContent")
            val keys2 = getKeysInBucket(sdk)
            assert(!keys2.contains("keyPutFileByContent"))
          }
        }

        // TODO: Fix proxy for copyObject function
        //        "check if object can be copied" in {
        //          sdk.putObject("demobucket", "keyCopyOrg", new File("file1mb.test"))
        //          sdk.copyObject("demobucket", "keyCopyOrg", "newbucket", "keyCopyDest")
        //
        //          val keys1 = getKeysInBucket("demobucket")
        //          assert(!keys1.contains("keyCopyOrg"))
        //          val keys2 = getKeysInBucket("newbucket")
        //          assert(keys2.contains("keyCopyDest"))
        //        }

        // TODO: Fix proxy for doesObjectExists function
        //        "check if object exists in bucket" in {
        //          sdk.putObject("demobucket", "keyCheckObjectExists", "content")
        //          assert(sdk.doesObjectExist("demobucket", "key"))
        //        }

        "put a 1MB file in a bucket (multi part upload)" in withSdkToTestProxy(awsSignerType) { sdk =>
          withFile(1024 * 1024) { filename =>
            doMultiPartUpload(sdk, filename, "keyMultiPart1MB")
            val objectKeys = getKeysInBucket(sdk)
            assert(objectKeys.contains("keyMultiPart1MB"))
          }
        }

        // TODO: reenable, sometimes fails still with `upload canceled` error
        //        "put a 100MB file in a bucket (multi part upload)" in {
        //          doMultiPartUpload("file100mb.test", "keyMultiPart100MB")
        //
        //          val objectKeys = getKeysInBucket()
        //          assert(objectKeys.contains("keyMultiPart100MB"))
        //        }

        // TODO: Fix 1GB multi part upload
        //        "put a 1GB file in a bucket (multi part upload)" in {
        //          doMultiPartUpload("file1gb.test", "keyMultiPart1GB")
        //
        //          val objectKeys = getKeysInBucket()
        //          assert(objectKeys.contains("keyMultiPart1GB"))
        //        }
      }
    }
  }
}
