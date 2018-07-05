package nl.wbaa.gargoyle.proxy

import nl.wbaa.gargoyle.proxy.providers.CephProvider

object Server {
  def main(args: Array[String]): Unit = {

    val server = new S3Proxy(8001, new CephProvider)
    server.start()

  }
}
