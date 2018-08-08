//package com.ing.wbaa.gargoyle.proxy.provider
//
//import akka.actor.ActorSystem
//import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
//import com.ing.wbaa.gargoyle.proxy.data._
//import com.ing.wbaa.testkit.docker.DockerRangerAdminService
//import com.whisk.docker.impl.spotify.DockerKitSpotify
//import com.whisk.docker.scalatest.DockerTestKit
//import org.scalatest.{Assertion, AsyncWordSpec, DiagrammedAssertions}
//
//import scala.concurrent.Future
//
//class AuthorizationProviderRangerItTest extends AsyncWordSpec with DiagrammedAssertions
//  with DockerTestKit
//  with DockerKitSpotify
//  with DockerRangerAdminService {
//  final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")
//
//  val s3Request = S3Request(
//    AwsRequestCredential(AwsAccessKey("accesskey"), Some(AwsSessionToken("sessiontoken"))),
//    Some("demobucket"),
//    Read
//  )
//
//  val user = User(
//    "testuser",
//    "secretKey",
//    Set("testgroup"),
//    "arn"
//  )
//
//  /**
//    * Fixture for setting up a Ranger provider object
//    *
//    * @param testCode      Code that accepts the created authorization provider
//    * @return Assertion
//    */
//  def withAuthorizationProviderRanger(testCode: AuthorizationProviderRanger => Future[Assertion]): Future[Assertion] = {
//    testCode(new AuthorizationProviderRanger {
//      override def rangerSettings: GargoyleRangerSettings = gargoyleRangerSettings
//    })
//  }
//
//  "Authorization Provider Ranger" should {
//    "authorize a request" that {
//      "successfully authorizes" in withAuthorizationProviderRanger { apr =>
//        assert(apr.isAuthorized(s3Request, user))
//      }
//    }
//  }
//}
