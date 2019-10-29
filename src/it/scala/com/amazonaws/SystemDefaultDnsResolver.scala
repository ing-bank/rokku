package com.amazonaws
import java.net.InetAddress

class SystemDefaultDnsResolver extends DnsResolver {
  override def resolve(host: String): Array[InetAddress] = Array(InetAddress.getByName("127.0.0.1"))
}
