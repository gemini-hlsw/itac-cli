package edu.gemini.tac.qengine.impl.resource

import edu.gemini.tac.qengine.p1.{ObsConditions, Target}
import edu.gemini.tac.qengine.impl.block.Block
import edu.gemini.tac.qengine.api.config.SiteSemesterConfig
import edu.gemini.tac.qengine.log.{RejectTarget, RejectMessage}
import edu.gemini.tac.qengine.util.{BoundedTime, Time}
import edu.gemini.tac.qengine.impl.queue.ProposalQueueBuilder
import xml.Elem

object RaResource {
  def apply(t: Time, c: SiteSemesterConfig): RaResource = {
    val decRes   = DecResourceGroup(t, c.decLimits)
    val condsRes = ConditionsResourceGroup(t, c.conditions)
    new RaResource(new BoundedTime(t), decRes, condsRes)
  }
}

/**
 * Groups the dec and obs conditions resources for a particular RA bin.
 *
 * Used as the parameterized type to RaResourceGroup
 */
final class RaResource(val absBounds: BoundedTime, val decRes: DecResourceGroup, val condsRes: ConditionsResourceGroup) extends Resource {
  type T = RaResource

  // There is an absolute time limit for the RA, but the time limit for a
  // dec (as indicated by a target) or for a particular set of observing
  // conditions, may be less than the absolute limit.

  def limit: Time                                  = absBounds.limit
  def limit(t: Target): Time                       = limit min decRes.limit(t)
  def limit(c: ObsConditions): Time                = limit min condsRes.limit(c)
  def limit(t: Target, c: ObsConditions): Time     = limit(t) min limit(c)

  // There is an absolute amount of time remaining for the RA, but the time
  // remaining for a particular dec (as indicated by a target) or for a
  // particular set of observing conditions, may be less than the absolute.

  def remaining: Time                              = absBounds.remaining
  def remaining(t: Target): Time                   = remaining min decRes.remaining(t)
  def remaining(c: ObsConditions): Time            = remaining min condsRes.remaining(c)
  def remaining(t: Target, c: ObsConditions): Time = remaining(t) min remaining(c)

  // If the RA bin is full, then it is full at any dec or observing conditions.
  // However, the RA as a whole may not be full yet a particular dec or set of
  // observing conditions may be full.

  def isFull: Boolean                              = absBounds.isFull
  def isFull(t: Target): Boolean                   = isFull || decRes.isFull(t)
  def isFull(c: ObsConditions): Boolean            = isFull || condsRes.isFull(c)
  def isFull(t: Target, c: ObsConditions): Boolean = isFull(t) || isFull(c)

  override def reserve(block: Block, queue: ProposalQueueBuilder): RejectMessage Either RaResource =
    absBounds.reserve(block.time) match {
      case None =>
        Left(new RejectTarget(block.prop, block.obs, queue.band, RejectTarget.Ra, absBounds.used, absBounds.limit))
      case Some(newAbsBounds) =>
        for {
          newDecRes   <- decRes.reserve(block, queue).right
          newCondsRes <- condsRes.reserve(block, queue).right
        } yield new RaResource(newAbsBounds, newDecRes, newCondsRes)
    }

  def reserveAvailable(time: Time, target: Target, conds: ObsConditions): (RaResource, Time) = {
    val (newAbs, rem1) = absBounds.reserveAvailable(time)
    val (newDec, rem2) = decRes.reserveAvailable(time, target)
    val (newCon, rem3) = condsRes.reserveAvailable(time, conds)
    (new RaResource(newAbs, newDec, newCon), rem1 max rem2 max rem3)
  }

  def toXML : Elem = <RaResource>
    { absBounds.toXML }
    { decRes.toXML }
    { condsRes.toXML }
    </RaResource>
}
