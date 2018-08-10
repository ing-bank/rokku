package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import com.ing.wbaa.gargoyle.proxy.config.GargoyleStsSettings
import com.ing.wbaa.gargoyle.proxy.data.{AwsAccessKey, AwsRequestCredential, AwsSessionToken, User}
import com.ing.wbaa.testkit.docker.DockerStsService
import com.whisk.docker.impl.spotify.DockerKitSpotify
import com.whisk.docker.scalatest.DockerTestKit
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationProviderSTSItTest extends AsyncWordSpec with DiagrammedAssertions
  with DockerTestKit
  with DockerKitSpotify
  with DockerStsService {
  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  /**
    * Fixture for setting up an sts provider object
    *
    * @param testCode      Code that accepts the created authentication provider
    * @return Assertion
    */
  def withAuthenticationProviderSts(testCode: AuthenticationProviderSTS => Future[Assertion]): Future[Assertion] = {
    gargoyleStsSettingsFuture
      .map(gargoyleStsSettings =>
        new AuthenticationProviderSTS {
          override implicit def system: ActorSystem = testSystem
          override implicit def executionContext: ExecutionContext = testSystem.dispatcher
          override implicit def materializer: Materializer = ActorMaterializer()(testSystem)
          override def stsSettings: GargoyleStsSettings = gargoyleStsSettings
        })(executionContext)
      .flatMap(aps => testCode(aps))(executionContext)
  }

  "Authentication Provider STS" should {
    "get a user" that {
      "successfully retrieves a user" in withAuthenticationProviderSts{ aps =>
        aps.getUserForAccessKey(AwsAccessKey("accesskey")).map { userResult =>
          assert(userResult.contains(User("testuser", "secretkey", Set("testgroup", "groupTwo"), "arn")))
        }(executionContext)
      }

      "return None when a user could not be found with STS" in withAuthenticationProviderSts { aps =>
        aps.getUserForAccessKey(AwsAccessKey("nonexistent")).map { userResult =>
          assert(userResult.isEmpty)
        }(executionContext)
      }
    }

    "check authentication" that {
      "succeeds for valid credentials" in withAuthenticationProviderSts { aps =>
        aps.areCredentialsAuthentic(AwsRequestCredential(AwsAccessKey("accesskey"), Some(AwsSessionToken("okSessionToken")))).map { userResult =>
          assert(userResult)
        }(executionContext)
      }

      "fail when no token is provided" in withAuthenticationProviderSts { aps =>
        aps.areCredentialsAuthentic(AwsRequestCredential(AwsAccessKey("accesskey"), None)).map { userResult =>
          assert(!userResult)
        }(executionContext)
      }

      "fail when user is not authenticated" in withAuthenticationProviderSts { aps =>
        aps.areCredentialsAuthentic(AwsRequestCredential(AwsAccessKey("notauthenticated"), Some(AwsSessionToken("okSessionToken")))).map { userResult =>
          assert(!userResult)
        }(executionContext)
      }
    }
  }
}
