package com.ing.wbaa.rokku.proxy.metrics

import com.codahale.metrics.{ JmxReporter, MetricRegistry }
//import com.codahale.metrics.jmx.JmxReporter

object MetricsFactory {

  val ALL_REQUEST = "requests.status.all.total"
  val SUCCESS_REQUEST = "requests.status.success.total"
  val FAILURE_REQUEST = "requests.status.failure.total"
  val UNAUTHENTICATED_REQUEST = "requests.status.unauthenticated.total"
  val REQUEST_TIME = "requests.nanoseconds.total"
  val REQUEST_TIME_HIST = "requests.time.histogram"
  val HTTP_METHOD = "{httpMethod}"
  val HTTP_DIRECTION = "{InOut}"
  val REQUEST_CONTEXT_LENGTH = s"requests.method.$HTTP_METHOD.$HTTP_DIRECTION.context.length.bytes"
  val REQUEST_CONTEXT_LENGTH_SUM = s"requests.method.$HTTP_METHOD.$HTTP_DIRECTION.context.length.bytes.total"

  private[this] val metrics = new MetricRegistry()

  JmxReporter.forRegistry(metrics).inDomain("rokku").build.start()

  def registryMetrics(): MetricRegistry = metrics

  def countRequest(name: String, count: Long = 1, countAll: Boolean = true): Unit = {
    metrics.counter(name).inc(count)
    if (countAll) metrics.counter(ALL_REQUEST).inc()
  }

  def markRequestTime(time: Long): Unit = {
    metrics.counter(REQUEST_TIME).inc(time)
    metrics.histogram(REQUEST_TIME_HIST).update(time)
  }
}
