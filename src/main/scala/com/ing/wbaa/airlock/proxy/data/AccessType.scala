package com.ing.wbaa.airlock.proxy.data

sealed class AccessType(val rangerName: String)

case object Read extends AccessType("read")
case object Write extends AccessType("write")
case object Delete extends AccessType("write")
case object NoAccess extends AccessType("noAccess")
