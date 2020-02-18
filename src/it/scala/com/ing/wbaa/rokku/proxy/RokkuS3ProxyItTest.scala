package com.ing.wbaa.rokku.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion
import com.amazonaws.services.s3.model.{AmazonS3Exception, DeleteObjectsRequest, GroupGrantee, Permission}
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest
import com.ing.wbaa.rokku.proxy.config._
import com.ing.wbaa.rokku.proxy.data.{RequestId, S3Request, User}
import com.ing.wbaa.rokku.proxy.handler.parsers.RequestParser
import com.ing.wbaa.rokku.proxy.handler.{FilterRecursiveListBucketHandler, RequestHandlerS3}
import com.ing.wbaa.rokku.proxy.provider._
import com.ing.wbaa.rokku.proxy.provider.aws.S3Client
import com.ing.wbaa.rokku.proxy.queue.MemoryUserRequestQueue
import com.ing.wbaa.testkit.RokkuFixtures
import com.ing.wbaa.testkit.awssdk.{S3SdkHelpers, StsSdkHelpers}
import com.ing.wbaa.testkit.oauth.{KeycloackToken, OAuth2TokenRequest}
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

class RokkuS3ProxyItTest extends AsyncWordSpec with DiagrammedAssertions
  with S3SdkHelpers
  with StsSdkHelpers
  with RokkuFixtures
  with OAuth2TokenRequest {

  import scala.collection.JavaConverters._

  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override implicit def executionContext: ExecutionContext = testSystem.dispatcher

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  val rokkuHttpSettings: HttpSettings = new HttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  private val validKeycloakCredentialsTestuser = Map(
    "grant_type" -> "password",
    "username" -> "testuser",
    "password" -> "password",
    "client_id" -> "sts-rokku"
  )

  private val validKeycloakCredentialsUserone = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> "sts-rokku"
  )

  private val validKeycloakCredentialsUsertwo = Map(
    "grant_type" -> "password",
    "username" -> "usertwo",
    "password" -> "password",
    "client_id" -> "sts-rokku"
  )

  /**
    * Fixture for starting and stopping a test proxy that tests can interact with.
    *
    * @param testCode Code that accepts the created STS sdk and an authority for an S3 sdk
    * @return Future[Assertion]
    */
  def withSdkToMockProxy(testCode: (AWSSecurityTokenService, Authority) => Future[Assertion]): Future[Assertion] = {
    val proxy: RokkuS3Proxy = new RokkuS3Proxy with RequestHandlerS3
      with FilterRecursiveListBucketHandler with AuthenticationProviderSTS
      with AuthorizationProviderRanger with LineageProviderAtlas with SignatureProviderAws
      with MessageProviderKafka with AuditLogProvider with MemoryUserRequestQueue with RequestParser {
      override implicit lazy val system: ActorSystem = testSystem
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

      override val requestPersistenceEnabled: Boolean = false
      override val configuredPersistenceId: String = "localhost-1"
    }
    proxy.startup.flatMap { binding =>
      val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
      testCode(getAmazonSTSSdk(StsSettings(testSystem).stsBaseUri), authority)
        .andThen { case _ => proxy.shutdown() }
    }
  }

  private def getSdk(stsSdk: AWSSecurityTokenService, s3ProxyAuthority: Authority, keycloakToken: KeycloackToken): AmazonS3 = {
    val cred = stsSdk.getSessionToken(new GetSessionTokenRequest()
      .withTokenCode(keycloakToken.access_token))
      .getCredentials

    val sessionCredentials = new BasicSessionCredentials(
      cred.getAccessKeyId,
      cred.getSecretAccessKey,
      cred.getSessionToken
    )

    getAmazonS3(s3ProxyAuthority, sessionCredentials)
  }

  val numberOfObjects = 1000

  private val generateMultideleteRequest = {
    val rand = new Random()
    val keys = new ListBuffer[String]()
    for (c <- 1 to numberOfObjects) keys +=
      s"testuser/one/two/three/four/five/six/seven/eight/nine/ten/eleven/twelve/sub$c/${rand.alphanumeric.take(32).mkString}=${rand.alphanumeric.take(12).mkString}.txt"
    keys
  }

  "Rokku" should {
    "not allow to multi delete others objects" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentialsUserone) flatMap { keycloackToken =>
        val s3Client = getSdk(stsSdk, s3ProxyAuthority, keycloackToken)

        import scala.collection.JavaConverters._
        val deleteRequest = new DeleteObjectsRequest("home")
        deleteRequest.setKeys(List(
          new KeyVersion("userone/issue"),
          new KeyVersion("testuser1/issue")
        ).asJava)

        assertThrows[AmazonS3Exception](s3Client.deleteObjects(deleteRequest))
      }
    }

    "allow to multi delete own objects" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentialsUserone) flatMap { keycloackToken =>
        val s3Client = getSdk(stsSdk, s3ProxyAuthority, keycloackToken)

        import scala.collection.JavaConverters._
        val deleteRequest = new DeleteObjectsRequest("home")
        deleteRequest.setKeys(List(
          new KeyVersion("userone/issue"),
        ).asJava)

        assert(s3Client.deleteObjects(deleteRequest).getDeletedObjects.asScala.length == 1)
      }
    }

    "multidelete for multiple hundreds of objects" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentialsTestuser) flatMap { keycloackToken =>
        val s3Client = getSdk(stsSdk, s3ProxyAuthority, keycloackToken)

        val deleteRequest = new DeleteObjectsRequest("home")
        deleteRequest.setKeys(generateMultideleteRequest.map(k => new KeyVersion(k)).asJava)
        deleteRequest.setQuiet(false)

        val r = s3Client.deleteObjects(deleteRequest)
        assert(r.getDeletedObjects.size() == numberOfObjects)
      }
    }

    "create a home bucket and files for userone and user two - users can see only own objects" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentialsTestuser).flatMap { keycloakTokenTestuser =>
        val testuserS3 = getSdk(stsSdk, s3ProxyAuthority, keycloakTokenTestuser)
        val objectForAll = List("mainfile")
        val userOneObjects = List("userone/", "userone/dir1/", "userone/dir1/file1")
        val userTwoObjects = List("usertwo/", "usertwo/dir2/", "usertwo/dir2/file2")
        withHomeBucket(testuserS3, objectForAll ++ userOneObjects ++ userTwoObjects) { bucket =>
          retrieveKeycloackToken(validKeycloakCredentialsUserone).map { keycloakToken =>
            val useroneS3 = getSdk(stsSdk, s3ProxyAuthority, keycloakToken)
            val returnedObjects = useroneS3.listObjects(bucket).getObjectSummaries.asScala.toList.map(_.getKey)
            objectForAll.foreach(obj => assert(returnedObjects.contains(obj)))
            userOneObjects.foreach(obj => assert(returnedObjects.contains(obj)))
            assert(returnedObjects.size == 4)
          }
          retrieveKeycloackToken(validKeycloakCredentialsUsertwo).map { keycloakToken =>
            val usertwoS3 = getSdk(stsSdk, s3ProxyAuthority, keycloakToken)
            val returnedObjects = usertwoS3.listObjects(bucket).getObjectSummaries.asScala.toList.map(_.getKey)
            objectForAll.foreach(obj => assert(returnedObjects.contains(obj)))
            userTwoObjects.foreach(obj => assert(returnedObjects.contains(obj)))
            assert(returnedObjects.size == 4)
          }
        }
      }
    }

    "set the default bucket ACL on bucket creation" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentialsTestuser) flatMap { keycloackToken =>
        val s3Client = getSdk(stsSdk, s3ProxyAuthority, keycloackToken)


        s3Client.createBucket("acltest")

        // This pause is necessary because the policy is set asynchronously
        Thread.sleep(5000)

        val radosS3client = new S3Client {
          override protected[this] def storageS3Settings: StorageS3Settings = StorageS3Settings(testSystem)
        }

        import scala.concurrent.duration._
        val testBucketACL = Await.result(radosS3client.getBucketAcl("acltest"), 5.seconds)

        val grants = testBucketACL.getGrantsAsList.asScala

        assert(!grants.exists(g => GroupGrantee.AllUsers.equals(g.getGrantee) && Permission.Read == g.getPermission))
        assert(!grants.exists(g => GroupGrantee.AllUsers.equals(g.getGrantee) && Permission.Write == g.getPermission))
        assert(grants.exists(g => GroupGrantee.AuthenticatedUsers.equals(g.getGrantee) && Permission.Read == g.getPermission))
        assert(grants.exists(g => GroupGrantee.AuthenticatedUsers.equals(g.getGrantee) && Permission.Write == g.getPermission))

        val testBucketPolicy = Await.result(radosS3client.getBucketPolicy(bucketName = "acltest"), 5.seconds)
        val policy = testBucketPolicy.getPolicyText
        assert(policy == """{"Statement": [{"Action": ["s3:GetObject"],"Effect": "Allow","Principal": "*","Resource": ["arn:aws:s3:::*"]}],"Version": "2012-10-17"}""")
      }
    }

    "usertwo can read data created by userone from a shared bucket" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      val sharedBucket = "shared"
      val sharedFile = "sharedFile"
      val sharedContent = "sharedContent"
      retrieveKeycloackToken(validKeycloakCredentialsUserone).flatMap { keycloakTokenUserone =>
        val s3ClientUserone = getSdk(stsSdk, s3ProxyAuthority, keycloakTokenUserone)
        s3ClientUserone.deleteObject(sharedBucket, sharedFile)
        s3ClientUserone.putObject(sharedBucket, sharedFile, sharedContent)

        retrieveKeycloackToken(validKeycloakCredentialsUsertwo).flatMap { keycloakTokenUserTwo =>
          val s3ClientUserTwo = getSdk(stsSdk, s3ProxyAuthority, keycloakTokenUserTwo)
          val sharedFileReadByTwo = s3ClientUserTwo.getObjectAsString(sharedBucket, sharedFile)
          assert(sharedFileReadByTwo == sharedContent)
        }
      }
    }

  }
}
