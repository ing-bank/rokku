package nl.wbaa.gargoyle.proxy

import com.typesafe.config.ConfigFactory
import nl.wbaa.gargoyle.proxy.providers.CephProvider

object Server {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load().getConfig("api.server")

    val server = new S3Proxy(config.getInt("port"), new CephProvider)
    server.start()

  }
}
