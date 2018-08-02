package nl.wbaa.gargoyle.proxy

import java.io.{File, RandomAccessFile}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.services.s3.AmazonS3
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import nl.wbaa.gargoyle.proxy.config.{GargoyleHttpSettings, GargoyleStorageS3Settings}
import nl.wbaa.testkit.docker.DockerCephS3Service
import nl.wbaa.testkit.s3sdk.S3SdkHelpers
import org.scalatest._

import scala.collection.JavaConverters._
import scala.concurrent.Future

class S3ProxyItTest extends AsyncWordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerCephS3Service
  with S3SdkHelpers {
  private[this] final implicit val system: ActorSystem = ActorSystem.create("test-system")

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  private[this] val gargoyleHttpSettings = new GargoyleHttpSettings(system.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  // Settings to connect to S3 storage, we have to wait for the docker container to retrieve the exposed port
  private[this] lazy val gargoyleStorageS3SettingsFuture: Future[GargoyleStorageS3Settings] =
    cephContainer.getPorts()(docker = dockerExecutor, ec = dockerExecutionContext)
      .map { portMapping =>
        new GargoyleStorageS3Settings(system.settings.config) {
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
      .map(gargoyleStorageS3Settings => GargoyleS3Proxy(gargoyleHttpSettings, gargoyleStorageS3Settings))(executionContext)
      .map(proxy => (proxy, proxy.bind))(executionContext)
      .flatMap { case (proxy, proxyBind) =>
        proxyBind.map { binding =>
          val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
          try {
            testCode(getAmazonS3(awsSignerType, authority))
          } finally {
            proxy.shutdown()
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
        val bucketInCeph = "demobucket"

        "list the current buckets" in withSdkToTestProxy(awsSignerType) { sdk =>
          assert(sdk.listBuckets().asScala.toList.map(_.getName) == List(bucketInCeph))
        }

        "create and remove a bucket" in withSdkToTestProxy(awsSignerType) { sdk =>
          val testBucket = "createbuckettest"
          sdk.createBucket(testBucket)
          assert(sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
          sdk.deleteBucket(testBucket)
          assert(!sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
        }

        "list files in a bucket" in withSdkToTestProxy(awsSignerType) { sdk =>
          val testKey = "keyListFiles"

          sdk.putObject(bucketInCeph, testKey, "content")
          val resultV2 = sdk.listObjectsV2(bucketInCeph).getObjectSummaries.asScala.toList.map(_.getKey)
          val result = sdk.listObjects(bucketInCeph).getObjectSummaries.asScala.toList.map(_.getKey)

          assert(resultV2.contains(testKey))
          assert(result.contains(testKey))
        }

        "check if bucket exists" in withSdkToTestProxy(awsSignerType) { sdk =>
          assert(sdk.doesBucketExistV2(bucketInCeph))
        }

        "put, get and delete an object from a bucket" in withSdkToTestProxy(awsSignerType) { sdk =>
          withFile(1024 * 1024) { filename =>
            val testKeyContent = "keyPutFileByContent"
            val testKeyFile = "keyPutFileByFile"
            val testContent = "content"

            // PUT
            sdk.putObject(bucketInCeph, testKeyContent, testContent)
            sdk.putObject(bucketInCeph, testKeyFile, new File(filename))

            // GET
            val checkContent = sdk.getObjectAsString(bucketInCeph, testKeyContent)
            assert(checkContent == testContent)
            val keys1 = getKeysInBucket(sdk)
            List(testKeyContent, testKeyFile).map(k => assert(keys1.contains(k)))

            // DELETE
            sdk.deleteObject(bucketInCeph, testKeyContent)
            val keys2 = getKeysInBucket(sdk)
            assert(!keys2.contains(testKeyContent))
          }
        }

        // TODO: Fix proxy for copyObject function
        //        "check if object can be copied" in {
        //          sdk.putObject(bucketInCeph, "keyCopyOrg", new File("file1mb.test"))
        //          sdk.copyObject(bucketInCeph, "keyCopyOrg", "newbucket", "keyCopyDest")
        //
        //          val keys1 = getKeysInBucket(bucketInCeph)
        //          assert(!keys1.contains("keyCopyOrg"))
        //          val keys2 = getKeysInBucket("newbucket")
        //          assert(keys2.contains("keyCopyDest"))
        //        }

        // TODO: Fix proxy for doesObjectExists function
        //        "check if object exists in bucket" in {
        //          sdk.putObject(bucketInCeph, "keyCheckObjectExists", "content")
        //          assert(sdk.doesObjectExist(bucketInCeph, "key"))
        //        }

        "put a 1MB file in a bucket (multi part upload)" in withSdkToTestProxy(awsSignerType) { sdk =>
          val testKey = "keyMultiPart1MB"

          withFile(1024 * 1024) { filename =>
            doMultiPartUpload(sdk, filename, testKey)
            val objectKeys = getKeysInBucket(sdk)
            assert(objectKeys.contains(testKey))
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
