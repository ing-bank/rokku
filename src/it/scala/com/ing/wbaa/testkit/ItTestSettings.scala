package com.ing.wbaa.testkit

import akka.actor.ActorSystem
import com.ing.wbaa.gargoyle.proxy.config.GargoyleHttpSettings

trait ItTestSettings {
  implicit def testSystem: ActorSystem

  // Settings for tests:
  //  - Force a random port to listen on.
  //  - Explicitly bind to loopback, irrespective of any default value.
  lazy val gargoyleHttpSettings: GargoyleHttpSettings = new GargoyleHttpSettings(testSystem.settings.config) {
    override val httpPort: Int = 0
    override val httpBind: String = "127.0.0.1"
  }
}
