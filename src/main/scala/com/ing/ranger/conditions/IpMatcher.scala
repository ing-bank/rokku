package com.ing.ranger.conditions

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.net.util.SubnetUtils
import org.apache.ranger.plugin.conditionevaluator.RangerAbstractConditionEvaluator
import org.apache.ranger.plugin.policyengine.RangerAccessRequest

import scala.util.{Failure, Success, Try}

class IpMatcher extends RangerAbstractConditionEvaluator with LazyLogging {

  import scala.collection.JavaConverters._

  private var cidrs: List[SubnetUtils#SubnetInfo] = List[SubnetUtils#SubnetInfo]()
  private var _allowAny: Boolean = false

  /**
    * Parses the conditions for a Ranger policy to a list of CIDR ranges
    */
  override def init(): Unit = {
    super.init()

    if (Try(condition.getValues.isEmpty).getOrElse(true)) {
      logger.debug("init: no policy condition or empty condition collection! Will match always!")
      _allowAny = true
    }
    else if (condition.getValues.contains("*")) {
      logger.debug("init: wildcard value found.  Will match always.")
      _allowAny = true
    }

    cidrs = condition.getValues.asScala.toList
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

  /**
    * Checks for a ranger request whether the remoteIpAddress is in the CIDR range specified in the Ranger policy.
    *
    * @param request Ranger request object
    * @return True if the remoteIpAddress fits in the CIDR range
    */
  override def isMatched(request: RangerAccessRequest): Boolean = {
    logger.debug(s"Checking whether RemoteIpAddress matches for: $request")

    if (_allowAny) {
      logger.debug("RemoteIpAddress matched! (allowAny flag is true)")
      true
    } else isRemoteAddressInCidrRange(request.getRemoteIPAddress)
  }

  private def isRemoteAddressInCidrRange(remoteIpAddress: String): Boolean = {
    val remoteIpInCidr = cidrs.exists(_.isInRange(remoteIpAddress))

    if(remoteIpInCidr)
      logger.debug(s"RemoteIpAddress $remoteIpAddress matches CIDR range")
    else
      logger.debug(s"RemoteIpAddress $remoteIpAddress doesn't match CIDR range")

    remoteIpInCidr
  }
}
