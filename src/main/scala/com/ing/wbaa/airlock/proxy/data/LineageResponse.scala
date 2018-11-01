package com.ing.wbaa.airlock.proxy.data

sealed trait LineageResponse

case class LineageGuidResponse(entityGUID: String) extends LineageResponse

case class LineagePostGuidResponse(
    serverGuid: String,
    bucketGuid: String,
    pseudoDir: String,
    fileGuid: String,
    processGuid: String) extends LineageResponse
