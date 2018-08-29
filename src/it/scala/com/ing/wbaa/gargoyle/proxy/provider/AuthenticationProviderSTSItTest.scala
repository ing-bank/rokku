package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.proxy.data._
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

  override val stsSettings: GargoyleStsSettings = GargoyleStsSettings(testSystem)

  private val validKeycloakCredentials = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> "sts-gargoyle"
  )

  def withAwsCredentialsValidInSTS(testCode: AwsRequestCredential => Future[Assertion]): Future[Assertion] = {
    val stsSdk = getAmazonSTSSdk(GargoyleStsSettings(testSystem).stsBaseUri)
    retrieveKeycloackToken(validKeycloakCredentials).flatMap { keycloakToken =>
      val cred = stsSdk.assumeRoleWithWebIdentity(new AssumeRoleWithWebIdentityRequest()
        .withRoleArn("arn:aws:iam::123456789012:role/user")
        .withProviderId("provider")
        .withRoleSessionName("sessionName")
        .withWebIdentityToken(keycloakToken.access_token))
        .getCredentials

      testCode(AwsRequestCredential(AwsAccessKey(cred.getAccessKeyId), AwsSessionToken(cred.getSessionToken)))
    }
  }

  "Authentication Provider STS" should {
    "get a user" that {
      "successfully retrieves a user" in {
        withAwsCredentialsValidInSTS { awsCredential =>
          getUserForAccessKey(awsCredential).map { userResult =>
            assert(userResult.contains(User("userone", Some("user"))))
          }
        }
      }

      "return None when a user could not be found with STS" in {
        getUserForAccessKey(AwsRequestCredential(AwsAccessKey("nonexistent"), AwsSessionToken("sessiontoken"))).map { userResult =>
          assert(userResult.isEmpty)
        }
      }
    }

    "check authentication" that {
      "succeeds for valid credentials" in {
        withAwsCredentialsValidInSTS { awsCredential =>
          areCredentialsAuthentic(awsCredential).map { userResult =>
            assert(userResult)
          }
        }
      }

      "fail when user is not authenticated" in {
        areCredentialsAuthentic(AwsRequestCredential(AwsAccessKey("notauthenticated"), AwsSessionToken("okSessionToken"))).map { userResult =>
          assert(!userResult)
        }
      }
    }
  }
}
