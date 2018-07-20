package nl.wbaa.gargoyle.proxy

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Server extends App {
  {
    val server = new S3Proxy
    Await.result(server.start(), Duration.Inf)
  }
}
