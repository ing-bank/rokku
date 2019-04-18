package com.ing.wbaa.rokku.proxy.metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jmx.JmxReporter

object MetricsFactory {

  val ALL_REQUEST = "rokku.requests.all"
  val SUCCESS_REQUEST = "rokku.requests.success"
  val FAILURE_REQUEST = "rokku.requests.failure"
  val UNAUTHENTICATED_REQUEST = "rokku.requests.unauthenticated"
  val REQUEST_TIME = "rokku.requests.sum.time"
  val REQUEST_TIME_HIST = "rokku.requests.time.histogram"
  val HTTP_METHOD = "{httpMethod}"
  val HTTP_DIRECTION = "{InOut}"
  val REQUEST_CONTEXT_LENGTH = s"rokku.requests.method.$HTTP_METHOD.$HTTP_DIRECTION.context.length"
  val REQUEST_CONTEXT_LENGTH_SUM = s"rokku.requests.method.$HTTP_METHOD.$HTTP_DIRECTION.sum.context.length"

  private[this] val metrics = new MetricRegistry()

  JmxReporter.forRegistry(metrics).build.start()

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
