package edu.gemini.tac.qengine.p1

import edu.gemini.tac.qengine.util.Time

/**
 * An amount of time categorized by target and conditions.
 */
trait CategorizedTime {
  def target: Target
  def conditions: ObsConditions
  def time: Time

  def toXml = <CategorizedTime time = {time.toString}>
    {target.toXml}
    {conditions.toXml}
    </CategorizedTime>
}
