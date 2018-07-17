package nl.wbaa.gargoyle.proxy


object Server extends App {
  {
    val server = new S3Proxy
    server.start()
  }
}
