package com.ing.wbaa.rokku.proxy.data

sealed class AccessType(val rangerName: String, val auditAction: String)

case class Read(override val auditAction: String = "") extends AccessType("read", auditAction)
case class Head(override val auditAction: String = "") extends AccessType("read", auditAction)
case class Write(override val auditAction: String = "") extends AccessType("write", auditAction)
case class Delete(override val auditAction: String = "") extends AccessType("write", auditAction)
case object NoAccess extends AccessType("noAccess", "noAccess")

