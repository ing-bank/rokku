package nl.wbaa.gargoyle.proxy

import com.typesafe.config.ConfigFactory

object Server extends App {
  {
    val configProxy = ConfigFactory.load().getConfig("proxy.server")
    val proxyInterface: String = configProxy.getString("interface")
    val proxyPort: Int = configProxy.getInt("port")

    val configS3 = ConfigFactory.load().getConfig("s3.server")
    val s3Host: String = configS3.getString("host")
    val s3Port: Int = configS3.getInt("port")

    val server = new S3Proxy(s3Host, s3Port)
    server.start(proxyInterface, proxyPort)
  }
}
