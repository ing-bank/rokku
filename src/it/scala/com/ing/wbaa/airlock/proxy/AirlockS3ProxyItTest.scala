package com.ing.wbaa.airlock.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.stream.ActorMaterializer
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{GroupGrantee, Permission}
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.GetSessionTokenRequest
import com.ing.wbaa.airlock.proxy.config._
import com.ing.wbaa.airlock.proxy.data.{S3Request, User}
import com.ing.wbaa.airlock.proxy.handler.{FilterRecursiveListBucketHandler, RequestHandlerS3}
import com.ing.wbaa.airlock.proxy.provider._
import com.ing.wbaa.airlock.proxy.provider.aws.S3Client
import com.ing.wbaa.testkit.AirlockFixtures
import com.ing.wbaa.testkit.awssdk.{S3SdkHelpers, StsSdkHelpers}
import com.ing.wbaa.testkit.oauth.{KeycloackToken, OAuth2TokenRequest}
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{Await, ExecutionContext, Future}

class AirlockS3ProxyItTest extends AsyncWordSpec with DiagrammedAssertions
  with S3SdkHelpers
  with StsSdkHelpers
  with AirlockFixtures
  with OAuth2TokenRequest {

  import scala.collection.JavaConverters._

  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override implicit def materializer: ActorMaterializer = ActorMaterializer()(testSystem)

  override implicit def executionContext: ExecutionContext = testSystem.dispatcher

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  val airlockHttpSettings: HttpSettings = new HttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  private val validKeycloakCredentialsTestuser = Map(
    "grant_type" -> "password",
    "username" -> "testuser",
    "password" -> "password",
    "client_id" -> "sts-airlock"
  )

  private val validKeycloakCredentialsUserone = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> "sts-airlock"
  )

  private val validKeycloakCredentialsUsertwo = Map(
    "grant_type" -> "password",
    "username" -> "usertwo",
    "password" -> "password",
    "client_id" -> "sts-airlock"
  )

  /**
    * Fixture for starting and stopping a test proxy that tests can interact with.
    *
    * @param testCode Code that accepts the created STS sdk and an authority for an S3 sdk
    * @return Future[Assertion]
    */
  def withSdkToMockProxy(testCode: (AWSSecurityTokenService, Authority) => Future[Assertion]): Future[Assertion] = {
    val proxy: AirlockS3Proxy = new AirlockS3Proxy with RequestHandlerS3
      with FilterRecursiveListBucketHandler with AuthenticationProviderSTS
      with AuthorizationProviderRanger with LineageProviderAtlas with SignatureProviderAws with MessageProviderKafka {
      override implicit lazy val system: ActorSystem = testSystem
      override val httpSettings: HttpSettings = airlockHttpSettings
      override val storageS3Settings: StorageS3Settings = StorageS3Settings(testSystem)
      override val stsSettings: StsSettings = StsSettings(testSystem)
      override val atlasSettings: AtlasSettings = AtlasSettings(testSystem)
      override val kafkaSettings: KafkaSettings = KafkaSettings(testSystem)

      override protected def rangerSettings: RangerSettings = RangerSettings(testSystem)

      override def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = {
        user match {
          case User(userName, _, _, _) if userName.value == "testuser" => true
          case _ => super.isUserAuthorizedForRequest(request, user)
        }
      }
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

    getAmazonS3("S3SignerType", s3ProxyAuthority, sessionCredentials)
  }

  "Airlock" should {
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
        val testBucketPolicy = Await.result(radosS3client.getBucketAcl("acltest"), 5.seconds)

        val grants = testBucketPolicy.getGrantsAsList.asScala

        assert(!grants.exists(g => GroupGrantee.AllUsers.equals(g.getGrantee) && Permission.Read == g.getPermission))
        assert(!grants.exists(g => GroupGrantee.AllUsers.equals(g.getGrantee) && Permission.Write == g.getPermission))
        assert(grants.exists(g => GroupGrantee.AuthenticatedUsers.equals(g.getGrantee) && Permission.Read == g.getPermission))
        assert(grants.exists(g => GroupGrantee.AuthenticatedUsers.equals(g.getGrantee) && Permission.Write == g.getPermission))
      }
    }
  }
}
