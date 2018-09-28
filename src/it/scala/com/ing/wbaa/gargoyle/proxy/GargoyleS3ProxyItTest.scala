package com.ing.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import akka.stream.ActorMaterializer
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import com.ing.wbaa.gargoyle.proxy.config.{GargoyleAtlasSettings, GargoyleHttpSettings, GargoyleStorageS3Settings, GargoyleStsSettings}
import com.ing.wbaa.gargoyle.proxy.data._
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandlerS3
import com.ing.wbaa.gargoyle.proxy.provider.{AuthenticationProviderSTS, LineageProviderAtlas}
import com.ing.wbaa.testkit.GargoyleFixtures
import com.ing.wbaa.testkit.awssdk.{S3SdkHelpers, StsSdkHelpers}
import com.ing.wbaa.testkit.oauth.OAuth2TokenRequest
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContext, Future}

class GargoyleS3ProxyItTest extends AsyncWordSpec with DiagrammedAssertions
  with S3SdkHelpers
  with StsSdkHelpers
  with GargoyleFixtures
  with OAuth2TokenRequest {

  import scala.collection.JavaConverters._

  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override implicit def materializer: ActorMaterializer = ActorMaterializer()(testSystem)

  override implicit def executionContext: ExecutionContext = testSystem.dispatcher

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  val gargoyleHttpSettings: GargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  private val validKeycloakCredentials = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> "sts-gargoyle"
  )

  /**
    * Fixture for starting and stopping a test proxy that tests can interact with.
    *
    * @param testCode Code that accepts the created STS sdk and an authority for an S3 sdk
    * @return Future[Assertion]
    */
  def withSdkToMockProxy(testCode: (AWSSecurityTokenService, Authority) => Future[Assertion]): Future[Assertion] = {
    val proxy: GargoyleS3Proxy = new GargoyleS3Proxy with RequestHandlerS3 with AuthenticationProviderSTS with LineageProviderAtlas {
      override implicit lazy val system: ActorSystem = testSystem
      override val httpSettings: GargoyleHttpSettings = gargoyleHttpSettings
      override val storageS3Settings: GargoyleStorageS3Settings = GargoyleStorageS3Settings(testSystem)
      override val stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)
      override val atlasSettings: GargoyleAtlasSettings = new GargoyleAtlasSettings(testSystem.settings.config)

      override def isUserAuthorizedForRequest(request: S3Request, user: User): Boolean = true
    }
    proxy.startup.flatMap { binding =>
      val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
      testCode(getAmazonSTSSdk(GargoyleStsSettings(testSystem).stsBaseUri), authority)
        .andThen { case _ => proxy.shutdown() }
    }
  }

  "Gargoyle S3 Proxy" should {
    "connect to ceph with credentials from STS (GetSessionToken)" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentials).map { keycloakToken =>
        val cred = stsSdk.getSessionToken(new GetSessionTokenRequest()
          .withTokenCode(keycloakToken.access_token))
          .getCredentials

        val sessionCredentials = new BasicSessionCredentials(
          cred.getAccessKeyId,
          cred.getSecretAccessKey,
          cred.getSessionToken
        )

        val s3Sdk = getAmazonS3("S3SignerType", s3ProxyAuthority, sessionCredentials)
        withBucket(s3Sdk) { testBucket =>
          assert(s3Sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
        }
      }
    }

    "connect to ceph with credentials from STS (AssumeRole)" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      retrieveKeycloackToken(validKeycloakCredentials).map { keycloakToken =>
        val cred = stsSdk.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
          .withRoleArn("arn:aws:iam::123456789012:role/user")
          .withProviderId("provider")
          .withRoleSessionName("sessionName")
          .withWebIdentityToken(keycloakToken.access_token))
          .getCredentials

        val sessionCredentials = new BasicSessionCredentials(
          cred.getAccessKeyId,
          cred.getSecretAccessKey,
          cred.getSessionToken
        )

        val s3Sdk = getAmazonS3("S3SignerType", s3ProxyAuthority, sessionCredentials)
        withBucket(s3Sdk) { testBucket =>
          assert(s3Sdk.listBuckets().asScala.toList.map(_.getName).contains(testBucket))
        }
      }
    }
  }
}
