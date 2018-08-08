package com.ing.wbaa.gargoyle.proxy

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Authority, Host}
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.{AssumeRoleWithWebIdentityRequest, GetSessionTokenRequest}
import com.ing.wbaa.gargoyle.proxy.config.{GargoyleHttpSettings, GargoyleStorageS3Settings, GargoyleStsSettings}
import com.ing.wbaa.gargoyle.proxy.data.{S3Request, User}
import com.ing.wbaa.gargoyle.proxy.handler.RequestHandlerS3
import com.ing.wbaa.gargoyle.proxy.provider.AuthenticationProviderSTS
import com.ing.wbaa.testkit.docker.{DockerCephS3Service, DockerStsService}
import com.ing.wbaa.testkit.s3sdk.{S3SdkHelpers, StsSdkHelpers}
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.Future

class GargoyleS3ProxyItTest extends AsyncWordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerStsService
  with DockerCephS3Service
  with S3SdkHelpers
  with StsSdkHelpers {

  import scala.collection.JavaConverters._

  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  val gargoyleHttpSettings: GargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }

  /**
    * Fixture for starting and stopping a test proxy that tests can interact with.
    *
    * @param testCode      Code that accepts the created sdk
    * @return Assertion
    */
  def withSdkToMockProxy(testCode: (AWSSecurityTokenService, Authority) => Assertion): Future[Assertion] =
    gargoyleStorageS3SettingsFuture
      .flatMap { gargoyleStorageS3Settings =>
        gargoyleStsSettingsFuture.flatMap { gargoyleStsSettings =>
          val proxy = new GargoyleS3Proxy with RequestHandlerS3 with AuthenticationProviderSTS {
            override implicit lazy val system: ActorSystem = testSystem
            override val httpSettings: GargoyleHttpSettings = gargoyleHttpSettings
            override val storageS3Settings: GargoyleStorageS3Settings = gargoyleStorageS3Settings
            override val stsSettings: GargoyleStsSettings = gargoyleStsSettings

            override def isAuthorized(request: S3Request, user: User): Boolean = true
          }
          proxy.startup.flatMap {
            binding =>
              val authority = Authority(Host(binding.localAddress.getAddress), binding.localAddress.getPort)
              try testCode(getAmazonSTSSdk(gargoyleStsSettings.stsBaseUri), authority)
              finally proxy.shutdown()
          }(executionContext)
        }(executionContext)
      }(executionContext)

  "Gargoyle S3 Proxy" should {
    "connect to ceph with credentials from STS (GetSessionToken)" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      val cred = stsSdk.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode("validToken"))
        .getCredentials

      val sessionCredentials = new BasicSessionCredentials(
        cred.getAccessKeyId,
        cred.getSecretAccessKey,
        cred.getSessionToken
      )

      val s3Sdk = getAmazonS3("S3SignerType", s3ProxyAuthority, sessionCredentials)
      assert(s3Sdk.listBuckets().asScala.toList.map(_.getName) == List("demobucket"))
    }

    "connect to ceph with credentials from STS (AssumeRole)" in withSdkToMockProxy { (stsSdk, s3ProxyAuthority) =>
      val cred = stsSdk.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken("validToken"))
        .getCredentials

      val sessionCredentials = new BasicSessionCredentials(
        cred.getAccessKeyId,
        cred.getSecretAccessKey,
        cred.getSessionToken
      )

      val s3Sdk = getAmazonS3("S3SignerType", s3ProxyAuthority, sessionCredentials)
      assert(s3Sdk.listBuckets().asScala.toList.map(_.getName) == List("demobucket"))
    }
  }
}
