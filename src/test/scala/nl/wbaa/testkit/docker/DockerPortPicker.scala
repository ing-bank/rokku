package nl.wbaa.testkit.docker

import java.net.ServerSocket

trait DockerPortPicker {
  def randomAvailablePort(): Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }
}
