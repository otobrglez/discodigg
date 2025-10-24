package discodigg

import zio.*
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.{Logger, LoggerFactory}

object LoggerSetup:
  private def setLogLevels: Task[Unit] = ZIO.attempt:
    val loggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val rootLogger    = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
    val nettyLogger   = loggerContext.getLogger("io.netty")

    rootLogger.setLevel(ch.qos.logback.classic.Level.INFO)
    nettyLogger.setLevel(ch.qos.logback.classic.Level.INFO)

    rootLogger.detachAndStopAllAppenders()
    val consoleAppender = new ConsoleAppender[ch.qos.logback.classic.spi.ILoggingEvent]()
    consoleAppender.setContext(loggerContext)
    consoleAppender.setName("STDOUT")

    val encoder = new PatternLayoutEncoder()
    encoder.setContext(loggerContext)
    encoder.setPattern("""%d{HH:mm:ss.SSS} %highlight(%-5level) %magenta(%logger{20}) - %msg%n""")
    encoder.start()

    consoleAppender.setEncoder(encoder)
    consoleAppender.start()

    rootLogger.addAppender(consoleAppender)

  def live: TaskLayer[Unit] = ZLayer.fromZIO(setLogLevels)
