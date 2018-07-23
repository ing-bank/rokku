package nl.wbaa.gargoyle.proxy.data

object AccessType extends Enumeration {
  type accessType = Value
  val write, read, write_acp, read_acp = Value
}
