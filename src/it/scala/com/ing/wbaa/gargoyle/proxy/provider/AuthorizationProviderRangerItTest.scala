package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data._
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.Future

class AuthorizationProviderRangerItTest extends AsyncWordSpec with DiagrammedAssertions {
  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  val s3Request = S3Request(
    AwsRequestCredential(AwsAccessKey("accesskey"), Some(AwsSessionToken("sessiontoken"))),
    Some("demobucket"),
    Read
  )

  val user = User(
    "testuser",
    Set("testgroup"),
  )

  /**
    * Fixture for setting up a Ranger provider object
    *
    * @param testCode      Code that accepts the created authorization provider
    * @return Assertion
    */
  def withAuthorizationProviderRanger(testCode: AuthorizationProviderRanger => Future[Assertion]): Future[Assertion] = {
    testCode(new AuthorizationProviderRanger {
      override def rangerSettings: GargoyleRangerSettings = GargoyleRangerSettings(testSystem)
    })
  }

  "Authorization Provider Ranger" should {
    "authorize a request" that {
      "successfully authorizes" in withAuthorizationProviderRanger { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user))
      }

      "doesn't authorize for requests without bucket" in withAuthorizationProviderRanger { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None), user))
      }

      "doesn't authorize for requests that are not supposed to be (Write)" in withAuthorizationProviderRanger { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(accessType = Write), user))
      }

      "doesn't authorize for unauthorized user and group" in withAuthorizationProviderRanger { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user.copy(userName = "unauthorized", userGroups = Set("unauthorized"))))
      }

      "does authorize for unauthorized user but authorized group" in withAuthorizationProviderRanger { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userName = "unauthorized")))
      }

      "does authorize for authorized user but unauthorized group" in withAuthorizationProviderRanger { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userGroups = Set("unauthorized"))))
      }
    }
  }
}