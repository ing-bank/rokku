package com.ing.wbaa.rokku.proxy.provider

import ch.qos.logback.classic.{ Level, Logger }
import com.ing.wbaa.rokku.proxy.data.RequestId
import com.ing.wbaa.rokku.proxy.handler.LoggerHandlerWithId
import org.scalatest.BeforeAndAfter
import org.scalatest.diagrams.Diagrams
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LoggerHandlerWithIdSpec extends AnyWordSpec with Matchers with Diagrams with BeforeAndAfter {

  private val logger = new LoggerHandlerWithId
  implicit val id: RequestId = RequestId("1")

  private val logRoot: Logger = org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
  private val currentLogLevel = logRoot.getLevel
  private val val1 = 1
  private val val2 = 2
  before(logRoot.setLevel(Level.DEBUG))
  after(logRoot.setLevel(currentLogLevel))

  "Logger" should {
    "work" in {

      noException should be thrownBy {

        logger.debug("test debug {}", val1)
        logger.debug("test debug {} {}", val1, val2)
        logger.debug("test debug {}", new RuntimeException("RTE").getMessage)

        logger.info("test info {}", val1)
        logger.info("test info {} {}", val1, val2)
        logger.info("test info {}", new RuntimeException("RTE").getMessage)

        logger.warn("test warn {}", val1)
        logger.warn("test warn {} {}", val1, val2)
        logger.warn("test warn {}", new RuntimeException("RTE").getMessage)

        logger.error("test error {}", val1)
        logger.error("test error {} {}", val1, val2)
        logger.error("test error {}", new RuntimeException("RTE").getMessage)
      }
    }
  }
}
