package com.ing.wbaa.rokku.proxy.data

sealed class AccessType(val rangerName: String, val auditAction: String)

case class Read(override val auditAction: String = "") extends AccessType("read", auditAction)
case class Head(override val auditAction: String = "") extends AccessType("read", auditAction)
class Write(override val auditAction: String = "") extends AccessType("write", auditAction)
case class Post(override val auditAction: String = "") extends Write(auditAction)
case class Put(override val auditAction: String = "") extends Write(auditAction)
case class Delete(override val auditAction: String = "") extends AccessType("write", auditAction)
case object NoAccess extends AccessType("noAccess", "noAccess")

