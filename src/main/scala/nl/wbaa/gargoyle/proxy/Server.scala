package nl.wbaa.gargoyle.proxy

import akka.actor.ActorSystem

object Server extends App {
  val proxy: GargoyleS3Proxy = GargoyleS3Proxy()(ActorSystem.create("gargoyle-s3proxy"))
}
