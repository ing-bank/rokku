package com.ing.wbaa.rokku.proxy.data

object HealthCheck {
  sealed trait HCMethod
  case object S3ListBucket extends HCMethod
  case object Default extends HCMethod
}
