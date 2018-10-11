package com.ing.wbaa.airlock.proxy.provider

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import com.ing.wbaa.airlock.proxy.config.StsSettings
import com.ing.wbaa.airlock.proxy.data._
import com.ing.wbaa.testkit.awssdk.StsSdkHelpers
import com.ing.wbaa.testkit.oauth.OAuth2TokenRequest
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationProviderSTSItTest extends AsyncWordSpec with DiagrammedAssertions
  with AuthenticationProviderSTS
  with StsSdkHelpers
  with OAuth2TokenRequest {
  override implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
  override implicit val system: ActorSystem = testSystem
  override implicit val executionContext: ExecutionContext = testSystem.dispatcher
  override implicit val materializer: ActorMaterializer = ActorMaterializer()(testSystem)

  override val stsSettings: StsSettings = StsSettings(testSystem)

  private val validKeycloakCredentials = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> "sts-gargoyle"
  )

  def withAwsCredentialsValidInSTS(testCode: AwsRequestCredential => Future[Assertion]): Future[Assertion] = {
    val stsSdk = getAmazonSTSSdk(StsSettings(testSystem).stsBaseUri)
    retrieveKeycloackToken(validKeycloakCredentials).flatMap { keycloakToken =>
      val cred = stsSdk.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn:aws:iam::123456789012:role/user")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken(keycloakToken.access_token))
        .getCredentials

      testCode(AwsRequestCredential(AwsAccessKey(cred.getAccessKeyId), Some(AwsSessionToken(cred.getSessionToken))))
    }
  }

  "Authentication Provider STS" should {
    "check authentication" that {
      "succeeds for valid credentials" in {
        withAwsCredentialsValidInSTS { awsCredential =>
          areCredentialsActive(awsCredential).map { userResult =>
            assert(userResult.map(_.userName).contains(UserName("userone")))
            assert(userResult.flatMap(_.userAssumedGroup).contains(UserAssumedGroup("user")))
            assert(userResult.exists(_.accessKey.value.length == 32))
            assert(userResult.exists(_.secretKey.value.length == 32))
          }
        }
      }

      "fail when user is not authenticated" in {
        areCredentialsActive(AwsRequestCredential(AwsAccessKey("notauthenticated"), Some(AwsSessionToken("okSessionToken")))).map { userResult =>
          assert(userResult.isEmpty)
        }
      }
    }
  }
}
