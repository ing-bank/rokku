package nl.wbaa.gargoyle.proxy

import com.typesafe.config.ConfigFactory

//we can move it later to conf package
trait conf {
  // proxy settings
  val proxyConfig = ConfigFactory.load().getConfig("proxy.server")

  // s3 settings
  val s3Config = ConfigFactory.load().getConfig("s3.settings")
  val s3endpoint = s3Config.getString("s3_endpoint")
  val s3endpoint_port = s3Config.getInt("s3_endpoint_port")
}

object Server extends App with conf {
  {
    //val config = ConfigFactory.load().getConfig("proxy.server")

    val server = new S3Proxy(proxyConfig.getInt("port"))
    server.start()
  }
}
