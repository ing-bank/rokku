package com.ing.wbaa.gargoyle.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleRangerSettings
import com.ing.wbaa.gargoyle.proxy.provider.AuthorizationProviderRanger.RangerException
import org.scalatest.{ DiagrammedAssertions, WordSpec }

class AuthorizationProviderSpec extends WordSpec with DiagrammedAssertions with AuthorizationProviderRanger {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override val rangerSettings: GargoyleRangerSettings = new GargoyleRangerSettings(testSystem.settings.config) {
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
