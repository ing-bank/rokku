package com.ing.wbaa.rokku.proxy.provider

import akka.actor.ActorSystem
import com.ing.wbaa.rokku.proxy.config.AccessControlProviderSettings
import com.ing.wbaa.rokku.proxy.provider.AccessControlProviderRanger.RangerException
import org.scalatest.diagrams.Diagrams
import org.scalatest.wordspec.AnyWordSpec

class RangerAccessControlProviderSpec extends AnyWordSpec with Diagrams with AccessControlProviderClassForName {

  private[this] final implicit val testSystem: ActorSystem = ActorSystem.create("test-system")

  override val accessControlProviderSettings: AccessControlProviderSettings = new AccessControlProviderSettings(testSystem.settings.config) {
    override val pluginParams: Map[String, String] = Map("appId" -> "nonexistent", "serviceType" -> "nonexistent")
  }

  "Authorization Provider" should {
    "throw a Ranger exception for unknown appId or serviceType" in {
      assertThrows[RangerException] {
        init()
      }
    }
  }
}
