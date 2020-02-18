package com.ing.wbaa.rokku.proxy.provider

import akka.actor.ActorSystem
import com.amazonaws.services.securitytoken.model.{AssumeRoleRequest, GetSessionTokenRequest}
import com.ing.wbaa.rokku.proxy.config.StsSettings
import com.ing.wbaa.rokku.proxy.data.{AwsAccessKey, AwsRequestCredential, AwsSessionToken, RequestId, UserAssumeRole, UserGroup, UserName}
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

  override val stsSettings: StsSettings = StsSettings(testSystem)

  implicit val requestId: RequestId = RequestId("test")

  private val validKeycloakCredentials = Map(
    "grant_type" -> "password",
    "username" -> "testuser",
    "password" -> "password",
    "client_id" -> "sts-rokku"
  )
  private val userOneKeycloakCredentials = Map(
    "grant_type" -> "password",
    "username" -> "userone",
    "password" -> "password",
    "client_id" -> "sts-rokku"
  )

  def withAwsCredentialsValidInSTS(testCode: AwsRequestCredential => Future[Assertion]): Future[Assertion] = {
    val stsSdk = getAmazonSTSSdk(StsSettings(testSystem).stsBaseUri)
    retrieveKeycloackToken(validKeycloakCredentials).flatMap { keycloakToken =>
      val cred = stsSdk.getSessionToken(new GetSessionTokenRequest()
        .withTokenCode(keycloakToken.access_token))
        .getCredentials

      testCode(AwsRequestCredential(AwsAccessKey(cred.getAccessKeyId), Some(AwsSessionToken(cred.getSessionToken))))
    }
  }

  def withAssumeRoleInSTS(testCode: AwsRequestCredential => Future[Assertion]): Future[Assertion] = {
    val stsSdk = getAmazonSTSSdk(StsSettings(testSystem).stsBaseUri)
    retrieveKeycloackToken(userOneKeycloakCredentials).flatMap { keycloakToken =>
      val assumeRoleReq = new AssumeRoleRequest().withTokenCode(keycloakToken.access_token)
      assumeRoleReq.setRoleArn("arn:aws:iam::account-id:role/admin")
      assumeRoleReq.setRoleSessionName("testRole")
      val cred = stsSdk.assumeRole(assumeRoleReq).getCredentials

      testCode(AwsRequestCredential(AwsAccessKey(cred.getAccessKeyId), Some(AwsSessionToken(cred.getSessionToken))))
    }
  }

  "Authentication Provider STS" should {
    "check authentication" that {
      "succeeds for valid credentials" in {
        withAwsCredentialsValidInSTS { awsCredential =>
          areCredentialsActive(awsCredential).map { userResult =>
            assert(userResult.map(_.userName).contains(UserName("testuser")))
            assert(userResult.map(_.userGroups).head.contains(UserGroup("testgroup")))
            assert(userResult.map(_.userGroups).head.contains(UserGroup("group3")))
            assert(userResult.map(_.userGroups).head.size == 2)
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

      "succeeds for valid role" in {
        withAssumeRoleInSTS { awsCredential =>
          areCredentialsActive(awsCredential).map { roleResult =>
            assert(roleResult.map(_.userRole).contains(UserAssumeRole("admin")))
            assert(roleResult.map(_.userGroups).contains(Set()))
            assert(roleResult.exists(_.accessKey.value.length == 32))
            assert(roleResult.exists(_.secretKey.value.length == 32))
          }
        }
      }
    }
  }
}
