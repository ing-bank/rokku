package com.ing.wbaa.airlock.proxy.provider.atlas

import com.typesafe.scalalogging.LazyLogging
import Model._
import com.ing.wbaa.airlock.proxy.data.LineageGuidResponse

import scala.concurrent.Future

trait LineageHelpers extends LazyLogging {

  def postEntity(atlasEntity: AtlasEntity): Future[LineageGuidResponse] = {

  }

}
