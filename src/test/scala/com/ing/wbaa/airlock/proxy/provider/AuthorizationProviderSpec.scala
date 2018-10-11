package com.ing.wbaa.airlock.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.airlock.proxy.config.RangerSettings
import com.ing.wbaa.airlock.proxy.provider.AuthorizationProviderRanger.RangerException
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class AuthorizationProviderSpec extends WordSpec with DiagrammedAssertions with AuthorizationProviderRanger {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override val rangerSettings: RangerSettings = new RangerSettings(testSystem.settings.config) {
    override val appId: String = "nonexistent"
    override val serviceType: String = "nonexistent"
  }

  "Authorization Provider" should {
    "throw a Ranger exception for unknown appId or serviceType" in {
      assertThrows[RangerException] {
        rangerPluginForceInit
      }
    }
  }
}
