package com.ing.wbaa.ranger.plugin.conditionevaluator

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.net.util.SubnetUtils
import org.apache.ranger.plugin.conditionevaluator.RangerAbstractConditionEvaluator
import org.apache.ranger.plugin.policyengine.RangerAccessRequest

import scala.util.{ Failure, Success, Try }

/**
 * This class will be called by Ranger upon a policy condition evaluation for IP ranges
 */
abstract class IpCidrMatcher extends RangerAbstractConditionEvaluator with LazyLogging {

  import scala.collection.JavaConverters._

  private var cidrs: List[SubnetUtils#SubnetInfo] = List[SubnetUtils#SubnetInfo]()
  private var _allowAny: Boolean = false

  /**
   * Parses the conditions for a Ranger policy to a list of CIDR ranges
   */
  override def init(): Unit = {
    super.init()

    if (Try(condition.getValues.isEmpty).getOrElse(true)) {
      logger.debug("No policy condition or empty condition values. Will match always!")
      _allowAny = true
    } else if (condition.getValues.contains("*")) {
      logger.debug("Wildcard value for policy found.  Will match always!")
      _allowAny = true
    } else {
      cidrs = condition
        .getValues.asScala.toList
        .flatMap { cidr =>
          logger.debug("Adding cidr: " + cidr)
          Try {
            val utils: SubnetUtils = new SubnetUtils(cidr)
            utils.setInclusiveHostCount(true)
            utils.getInfo
          } match {
            case Success(value) => Some(value)
            case Failure(exc) =>
              logger.warn("Skipping invalid cidr range: " + cidr, exc)
              None
          }
        }
    }
  }

  /**
   * Checks for a ranger request whether the remoteIpAddress is in the CIDR range specified in the Ranger policy.
   *
   * @param request Ranger request object
   * @return True if the remoteIpAddress fits in the CIDR range
   */
  override def isMatched(request: RangerAccessRequest): Boolean = {
    logger.debug(s"Checking whether RemoteIpAddress (${request.getRemoteIPAddress}) matches any CIDR range")

    if (_allowAny) {
      logger.debug("RemoteIpAddress matched! (allowAny flag is true)")
      true
    } else {
      val addresses = request.getRemoteIPAddress +: request.getForwardedAddresses.asScala.toList

      addresses.foldLeft(zero) { (a, b) =>
        combine(a, isRemoteAddressInCidrRange(b))
      }
    }
  }

  protected val zero: Boolean
  protected def combine(a: => Boolean, b: => Boolean): Boolean

  private def isRemoteAddressInCidrRange(remoteIpAddress: String): Boolean = {
    val remoteIpInCidr = cidrs
      .exists { cidr =>
        val inRange = cidr.isInRange(remoteIpAddress)
        if (inRange) logger.debug(s"RemoteIpAddress $remoteIpAddress matches CIDR range ${cidr.getCidrSignature}")
        inRange
      }

    if (!remoteIpInCidr) logger.debug(s"RemoteIpAddress $remoteIpAddress doesn't match any CIDR range")
    remoteIpInCidr
  }
}
