package edu.gemini.tac.qengine.impl


import java.io.StringWriter
import org.apache.logging.log4j._
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.appender.WriterAppender


object QueueCalculationLog{
  // private val writer = new StringWriter();
  // private val layout = new PatternLayout("<LogMessage time=\"%d{ISO8601}\"><Priority>%p</Priority><Text>%m%n</Text></LogMessage>")
  // private val appender = new WriterAppender(layout, writer)
  // appender.setName("QueueCalculatorLogAppender")
  // appender.setThreshold(Priority.DEBUG)

  private val _logger = LogManager.getLogger("QueueCalculationLogger")
  // _logger.setLevel(Level.INFO)
  //TODO: Switch to a non-resource-intense appender. StringWriter gobbles heap.
  //_logger.addAppender(appender)

  //TODO: Confirm that logger is only initialized once
  def logger = _logger

  // def log : String = writer.getBuffer.toString
}
