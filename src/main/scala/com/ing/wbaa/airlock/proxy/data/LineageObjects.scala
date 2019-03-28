package com.ing.wbaa.airlock.proxy.data

case class LineageObjectGuids(
    serverGuid: Long,
    bucketGuid: Long,
    pseudoDir: Long,
    objectGuid: Long,
    processGuid: Long,
    externalPathGuid: Long
)

object LineageObjectGuids {
  def apply(): LineageObjectGuids = LineageObjectGuids(System.nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime())
}
