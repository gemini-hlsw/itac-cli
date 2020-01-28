package edu.gemini.tac.qengine.log

import edu.gemini.tac.qengine.p1.{QueueBand, Observation, Proposal}
import edu.gemini.tac.qengine.util.Time

object RejectToo {
  val name = "ToO Remaining Time"

  private val detailTemplate =
    "ToO observation of %.2f hours with conditions %s.  Remaining time %.2f hours."
  def detail(prop: Proposal, obs: Observation, band: QueueBand, remaining: Time): String = {
    val obsTime = prop.relativeObsTime(obs, band).toHours.value
    val remTime = remaining.toHours.value
    detailTemplate.format(obsTime, obs.conditions, remTime)
  }

}

final case class RejectToo(prop: Proposal, obs: Observation, band: QueueBand, remaining: Time)
    extends ObsRejectMessage {
  def reason: String    = RejectToo.name
  def detail: String    = RejectToo.detail(prop, obs, band, remaining)
  override def toString = s"RejectToo($detail)"
}
