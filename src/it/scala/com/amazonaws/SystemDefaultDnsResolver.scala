package com.amazonaws
import java.net.InetAddress

/**
 * For integration test (virtual-hosted-style) all dns names are resoled as localhost
 * If you want to test locally the virtual-hosted-style using aws cli
 * set aws config:
 *   s3 = addressing_style = virtual
 * add to /etc/hosts all your buckets as hostname eg:
 *   127.0.0.1       localhost s3.localhost home.s3.localhost demobucket.s3.localhost shared.s3.localhost
 */
class SystemDefaultDnsResolver extends DnsResolver {
  override def resolve(host: String): Array[InetAddress] = Array(InetAddress.getByName("127.0.0.1"))
}
