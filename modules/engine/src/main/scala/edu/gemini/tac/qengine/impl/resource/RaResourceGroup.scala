package edu.gemini.tac.qengine.impl.resource

import edu.gemini.tac.qengine.impl.block.{TooBlocks, Block}
import edu.gemini.tac.qengine.util.Time
import edu.gemini.tac.qengine.log.{RejectToo, RejectMessage}
import edu.gemini.tac.qengine.impl.queue.ProposalQueueBuilder
import edu.gemini.tac.qengine.p1._
import edu.gemini.tac.qengine.api.config.{SiteSemesterConfig, RaBinGroup}
import org.slf4j.LoggerFactory

object RaResourceGroup {
  // Creates an RA resource group from the site/semester configuration.
  def apply(c: SiteSemesterConfig): RaResourceGroup =
    new RaResourceGroup(c.raLimits.map(RaResource(_, c)))
}

/**
 * TODO: Rename "SpatialBinResourceGroup"?
 *
 * A resource that encapsulates RaBinGroup[RaResource] (n.b. RaResource contains a DecResourceGroup encapsulating a DecBinGroup)
 */
case class RaResourceGroup(val grp: RaBinGroup[RaResource]) extends Resource {
  type T = RaResourceGroup

  val LOG = LoggerFactory.getLogger(getClass)

  def reserve(block: Block, queue: ProposalQueueBuilder): RejectMessage Either RaResourceGroup = {
    if (block.prop.too != Too.none) reserveToo(block, queue) else reserveNonToo(block, queue)
  }

  // Splits the block into one block/RaReservation according to the amount of
  // time to distribute to each RaReservation.  If the split is successful,
  // then we can record time in each of the RaReservations.  Otherwise, the
  // Too observation cannot be scheduled.
  private def reserveToo(
    block: Block,
    queue: ProposalQueueBuilder
  ): RejectMessage Either RaResourceGroup =
    tooBlocks(block) match {
      case None => {
        val sum = grp.bins.foldLeft(Time.hours(0))(_ + _.remaining(block.obs.conditions))
        Left(new RejectToo(block.prop, block.obs, queue.band, sum))
      }
      case Some(s) =>
        Right(
          new RaResourceGroup(
            RaBinGroup(
              grp.bins.zip(s) map {
                case (raResr, blk) => raResr.reserve(blk, queue).right.get
              }
            )
          )
        )
    }

  private def reserveNonToo(
    block: Block,
    queue: ProposalQueueBuilder
  ): RejectMessage Either RaResourceGroup = {
    val ra = block.obs.target.ra
    grp(ra).reserve(block, queue) match {
      case Right(bin) =>
        LOG.debug(s"    💚  I was able to reserve time for this RA ($ra).")
        Right(new RaResourceGroup(grp.updated(ra, bin)))
      case Left(err) =>
        LOG.debug(s"    ❌  I was unable to reserve time for this RA ($ra).")
        Left(err)
    }
  }

  def tooBlocks(block: Block): Option[Seq[Block]] =
    TooBlocks[RaResource](block, grp.bins, _.remaining(block.obs.conditions))

  /**
   * Reserves up-to the given amount of time, returning an updated
   * RaResourceGroup and any time left over that could not be reserved.
   */
  def reserveAvailable(
    time: Time,
    target: Target,
    conds: ObsConditions
  ): (RaResourceGroup, Time) = {
    val (bin, rem) = grp(target.ra).reserveAvailable(time, target, conds)
    (new RaResourceGroup(grp.updated(target.ra, bin)), rem)
  }

  def reserveAvailable[U <% CategorizedTime](reduction: U): (RaResourceGroup, Time) =
    reserveAvailable(reduction.time, reduction.target, reduction.conditions)

  def reserveAvailable[U <% CategorizedTime](reductions: List[U]): (RaResourceGroup, Time) = {
    reductions.foldLeft((this, Time.Zero)) {
      case ((grp0, time), reduction) =>
        grp0.reserveAvailable(reduction) match {
          case (newGrp, leftover) => (newGrp, leftover + time)
        }
    }
  }

  def toXML = <RaResourceGroup>
    {grp.toXML}
    </RaResourceGroup>
}
