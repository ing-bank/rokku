package com.ing.wbaa.rokku.proxy.provider

import com.github.benmanes.caffeine.cache.Caffeine
import com.ing.wbaa.rokku.proxy.data.{ AwsRequestCredential, RequestId, User }
import scalacache._
import scalacache.caffeine.CaffeineCache
import scalacache.modes.scalaFuture._

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

trait AuthenticationCachedProviderSTS extends AuthenticationProviderSTS {

  private val stsCacheConfig =
    Caffeine.newBuilder().
      maximumSize(10000).
      build[String, Entry[Future[Option[User]]]]
  private implicit val stsCache: Cache[Future[Option[User]]] = CaffeineCache(stsCacheConfig)

  override protected[this] def areCredentialsActive(awsRequestCredential: AwsRequestCredential)(implicit id: RequestId): Future[Option[User]] = {
    caching(keyParts = awsRequestCredential)(ttl = Some(stsSettings.cacheTTLInSeconds.second))(super.areCredentialsActive(awsRequestCredential)).flatten
  }
}
