package nl.wbaa.gargoyle.proxy

import com.typesafe.config.ConfigFactory

object Server extends App {
  {
    val config = ConfigFactory.load().getConfig("proxy.server")

    val server = new S3Proxy(config.getInt("port"))
    server.start()
  }
}
