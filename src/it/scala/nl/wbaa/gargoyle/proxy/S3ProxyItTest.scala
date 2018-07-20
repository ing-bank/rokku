package nl.wbaa.gargoyle.proxy

import java.io.{File, RandomAccessFile}

import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import nl.wbaa.testkit.docker.DockerCephS3Service
import nl.wbaa.testkit.s3sdk.sdkSetup
import org.scalatest._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class S3ProxyItTest extends WordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerCephS3Service
  with BeforeAndAfterAll
  with sdkSetup {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val filesWithSizes: Map[String, Long] = Map(
    "file1mb.test" -> 1024 * 1024,
    "file100mb.test" -> 100 * 1024 * 1024,
    "file1gb.test" -> 1024 * 1024 * 1024
  )

  override def beforeAll(): Unit = {
    logger.info(s"Starting Ceph docker images and S3 Proxy")

    // Create files for testing
    filesWithSizes.foreach { f =>
      val file = new RandomAccessFile(f._1, "rw")
      file.setLength(f._2)
      file.close()
    }

    // Start our proxy
    new S3Proxy().start()

    // Start docker containers mixed in with traits
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    // Remove files created for testing
    filesWithSizes.keys.foreach(new File(_).delete())
  }

  "S3 Proxy" should {
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
      s"proxy with $awsSignerType" that {
        val sdk = getAmazonS3(awsSignerType)

        def getKeysInBucket(bucket: String = "demobucket"): List[String] =
          sdk
            .listObjectsV2(bucket)
            .getObjectSummaries
            .asScala.toList
            .map(_.getKey)

        "list the current buckets" in {
          assert(sdk.listBuckets().asScala.toList.map(_.getName) == List("demobucket"))
        }

        "create and remove a bucket" in {
          sdk.createBucket("createbuckettest")
          assert(sdk.listBuckets().asScala.toList.map(_.getName).contains("createbuckettest"))
          sdk.deleteBucket("createbuckettest")
          assert(!sdk.listBuckets().asScala.toList.map(_.getName).contains("createbuckettest"))
        }

        "list files in a bucket" in {
          sdk.putObject("demobucket", "keyListFiles", "content")
          val resultV2 = sdk.listObjectsV2("demobucket").getObjectSummaries.asScala.toList.map(_.getKey)
          val result = sdk.listObjects("demobucket").getObjectSummaries.asScala.toList.map(_.getKey)

          assert(resultV2.contains("keyListFiles"))
          assert(result.contains("keyListFiles"))
        }

        "check if bucket exists" in {
          assert(sdk.doesBucketExistV2("demobucket"))
        }

        "put, get and delete an object from a bucket" in {
          // PUT
          sdk.putObject("demobucket", "keyPutFileByContent", "content")
          sdk.putObject("demobucket", "keyPutFileByFile", new File("file1mb.test"))

          // GET
          val checkContent = sdk.getObjectAsString("demobucket", "keyPutFileByContent")
          assert(checkContent == "content")
          val keys1 = getKeysInBucket()
          List("keyPutFileByContent", "keyPutFileByFile").map(k => assert(keys1.contains(k)))

          // DELETE
          sdk.deleteObject("demobucket", "keyPutFileByContent")
          val keys2 = getKeysInBucket()
          assert(!keys2.contains("keyPutFileByContent"))
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

        def doMultiPartUpload(file: String, key: String) = {
          val upload = TransferManagerBuilder
            .standard()
            .withS3Client(sdk)
            .build()
            .upload("demobucket", key, new File(file))

          upload.waitForUploadResult()
        }

        "put a 1MB file in a bucket (multi part upload)" in {
          doMultiPartUpload("file1mb.test", "keyMultiPart1MB")

          val objectKeys = getKeysInBucket()
          assert(objectKeys.contains("keyMultiPart1MB"))
        }

        "put a 100MB file in a bucket (multi part upload)" in {
          doMultiPartUpload("file100mb.test", "keyMultiPart100MB")

          val objectKeys = getKeysInBucket()
          assert(objectKeys.contains("keyMultiPart100MB"))
        }

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
