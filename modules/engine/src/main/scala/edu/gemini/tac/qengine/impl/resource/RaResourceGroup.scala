package edu.gemini.tac.qengine.impl.resource

import edu.gemini.tac.qengine.impl.block.{TooBlocks, Block}
import edu.gemini.tac.qengine.util.Time
import edu.gemini.tac.qengine.log.{RejectToo, RejectMessage}
import edu.gemini.tac.qengine.impl.queue.ProposalQueueBuilder
import edu.gemini.tac.qengine.p1._
import edu.gemini.tac.qengine.api.config.{SiteSemesterConfig, RaBinGroup}

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
class RaResourceGroup(val grp: RaBinGroup[RaResource]) extends Resource {
  type T = RaResourceGroup

  def reserve(block: Block, queue: ProposalQueueBuilder): RejectMessage Either RaResourceGroup =
    if (block.prop.too != Too.none) reserveToo(block, queue) else reserveNonToo(block, queue)

  // Splits the block into one block/RaReservation according to the amount of
  // time to distribute to each RaReservation.  If the split is successful,
  // then we can record time in each of the RaReservations.  Otherwise, the
  // Too observation cannot be scheduled.
  private def reserveToo(block: Block, queue: ProposalQueueBuilder): RejectMessage Either RaResourceGroup =
    tooBlocks(block) match {
        case None => {
          val sum = (Time.hours(0)/:grp.bins)(_ + _.remaining(block.obs.conditions))
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

  private def reserveNonToo(block: Block, queue: ProposalQueueBuilder): RejectMessage Either RaResourceGroup = {
    val ra = block.obs.target.ra
    grp(ra).reserve(block, queue).right.map(bin => new RaResourceGroup(grp.updated(ra, bin)))
  }

  def tooBlocks(block: Block): Option[Seq[Block]] =
    TooBlocks[RaResource](block, grp.bins, _.remaining(block.obs.conditions))

  /**
   * Reserves up-to the given amount of time, returning an updated
   * RaResourceGroup and any time left over that could not be reserved.
   */
  def reserveAvailable(time: Time, target: Target, conds: ObsConditions): (RaResourceGroup, Time) = {
    val (bin, rem) = grp(target.ra).reserveAvailable(time, target, conds)
    (new RaResourceGroup(grp.updated(target.ra, bin)), rem)
  }

  def reserveAvailable[T <% CategorizedTime](reduction: T): (RaResourceGroup, Time) =
    reserveAvailable(reduction.time, reduction.target, reduction.conditions)

  def reserveAvailable[T <% CategorizedTime](reductions: List[T]): (RaResourceGroup, Time) = {
    ((this,Time.Zero)/:reductions) {
      case ((grp0, time), reduction) =>
        grp0.reserveAvailable(reduction) match {
          case (newGrp, leftover) => (newGrp, leftover+time)
        }
    }
  }

  def toXML = <RaResourceGroup>
    { grp.toXML }
    </RaResourceGroup>
}
