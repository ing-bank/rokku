package nl.wbaa.gargoyle.proxy

import com.typesafe.config.ConfigFactory
import nl.wbaa.gargoyle.proxy.providers.CephProvider

object Server extends App {
  {
    val config = ConfigFactory.load().getConfig("proxy.server")

    val server = new S3Proxy(config.getString("listen_on"), config.getInt("port"), new CephProvider)
    server.start()
  }
}
