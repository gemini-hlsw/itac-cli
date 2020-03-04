package edu.gemini.tac.qengine.p1

import edu.gemini.model.p1.{immutable => im}
import edu.gemini.tac.qengine.util.Time

import scalaz._
import Scalaz._

package object io {
  implicit class TimeAmountPimp(t: im.TimeAmount) extends AnyRef {
    def nonNegativeQueueEngineTime(name: String): ValidationNel[String, Time] =
      if (t.hours < 0.0) s"Negative $name amount: ${t.hours} hours".failureNel
      else Time.hours(t.hours).successNel
  }
}
