package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.data._
import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}

import scala.concurrent.Future

class AuthorizationProviderRangerItTest extends AsyncWordSpec with DiagrammedAssertions {
  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  val s3Request = S3Request(
    AwsRequestCredential(AwsAccessKey("accesskey"), AwsSessionToken("sessiontoken")),
    Some("demobucket"),
    None,
    Read
  )

  val user = User(
    "testuser",
    Some("testgroup"),
  )

  /**
    * Fixture for setting up a Ranger provider object
    *
    * @param testCode      Code that accepts the created authorization provider
    * @return Assertion
    */
  def withAuthorizationProviderRanger(rangerTestSettings: GargoyleRangerSettings = GargoyleRangerSettings(testSystem))(testCode: AuthorizationProviderRanger => Future[Assertion]): Future[Assertion] = {
    testCode(new AuthorizationProviderRanger {
        override def rangerSettings: GargoyleRangerSettings = rangerTestSettings
    })
  }

  "Authorization Provider Ranger" should {
    "authorize a request" that {
      "successfully authorizes a request on a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user))
      }

      "successfully authorizes a request on an object in a bucket" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = Some("object")), user))
      }

      "doesn't authorize for requests without bucket" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None), user))
      }

      "doesn't authorize for requests that are not supposed to be (Write)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(accessType = Write), user))
      }

      "doesn't authorize for unauthorized user and group" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request, user.copy(userName = "unauthorized", userGroups = Some("unauthorized"))))
      }

      "does authorize for unauthorized user but authorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userName = "unauthorized")))
      }

      "does authorize for authorized user but unauthorized group" in withAuthorizationProviderRanger() { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request, user.copy(userGroups = Some("unauthorized"))))
      }

      "doesn't authorize allow-list-buckets with default settings" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None, bucketObjectRoot = None, accessType = Read), user))
      }

      "does authorize allow-list-buckets set to true" in withAuthorizationProviderRanger(new GargoyleRangerSettings(testSystem.settings.config) {
        override val listBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucket = None, bucketObjectRoot = None, accessType = Read), user))
      }

      "does authorize allow-create-buckets set to true" in withAuthorizationProviderRanger(new GargoyleRangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = Write), user))
      }

      "does authorize delete buckets set to true" in withAuthorizationProviderRanger(new GargoyleRangerSettings(testSystem.settings.config) {
        override val createBucketsEnabled: Boolean = true
      }) { apr =>
        assert(apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = Delete), user))
      }

      "doesn't authorize when method is not REST (GET, PUT, DELETE etc.)" in withAuthorizationProviderRanger() { apr =>
        assert(!apr.isUserAuthorizedForRequest(s3Request.copy(bucketObjectRoot = None, accessType = NoAccess), user))
      }

    }
  }
}
