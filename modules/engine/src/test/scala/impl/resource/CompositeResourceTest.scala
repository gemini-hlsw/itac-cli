package edu.gemini.tac.qengine.impl.resource

import edu.gemini.tac.qengine.impl.block.Block
import edu.gemini.tac.qengine.log.RejectMessage
import edu.gemini.tac.qengine.p1._

import org.junit._
import Assert._
import edu.gemini.tac.qengine.util.{BoundedTime, Time}
import edu.gemini.tac.qengine.impl.queue.ProposalQueueBuilder
import edu.gemini.spModel.core.Site

class CompositeResourceTest {
  import edu.gemini.tac.qengine.ctx.TestPartners._
  val partners = All

  class Reject(val prop: Proposal) extends RejectMessage {
    val reason = "Reject Message"
    val detail = "Details go here."
  }

  class BoundedTimeReservation1(val bounds: BoundedTime) extends Resource {
    type T = BoundedTimeReservation1
    def reserve(
      block: Block,
      queue: ProposalQueueBuilder
    ): RejectMessage Either BoundedTimeReservation1 =
      bounds.reserve(block.time) match {
        case None     => Left(new Reject(block.prop))
        case Some(bt) => Right(new BoundedTimeReservation1(bt))
      }
  }

  class BoundedTimeReservation2(val bounds: BoundedTime) extends Resource {
    type T = BoundedTimeReservation2
    def reserve(
      block: Block,
      queue: ProposalQueueBuilder
    ): RejectMessage Either BoundedTimeReservation2 =
      bounds.reserve(block.time) match {
        case None     => Left(new Reject(block.prop))
        case Some(bt) => Right(new BoundedTimeReservation2(bt))
      }
  }

  private val ntac   = Ntac(US, "x", 0, Time.hours(10))
  private val target = Target(0.0, 0.0) // not used
  private val conds  = ObsConditions.AnyConditions
  private val prop = CoreProposal(
    ntac,
    site = Site.GS,
    obsList = List(Observation(target, conds, Time.hours(10)))
  )

  @Test def testReserve() {
    val btr1 = new BoundedTimeReservation1(BoundedTime(Time.hours(1)))
    val btr2 = new BoundedTimeReservation2(BoundedTime(Time.hours(2)))
    val comp = new CompositeResource(btr1, btr2)

    val block = Block(prop, prop.obsList.head, Time.minutes(15))

    comp.reserve(block, Fixture.emptyQueue) match {
      case Right(newComp) => {
        assertEquals(Time.minutes(45), newComp._1.bounds.remaining)
        assertEquals(Time.minutes(105), newComp._2.bounds.remaining)
      }
      case _ => fail()
    }
  }

  @Test def testRejectFirst() {
    val btr1 = new BoundedTimeReservation1(BoundedTime(Time.hours(1)))
    val btr2 = new BoundedTimeReservation2(BoundedTime(Time.hours(2)))
    val comp = new CompositeResource(btr1, btr2)

    val block = Block(prop, prop.obsList.head, Time.minutes(61))

    comp.reserve(block, Fixture.emptyQueue) match {
      case Left(msg: Reject) => // ok
      case _                 => fail()
    }
  }

  @Test def testRejectSecond() {
    val btr1 = new BoundedTimeReservation1(BoundedTime(Time.hours(2)))
    val btr2 = new BoundedTimeReservation2(BoundedTime(Time.hours(1)))
    val comp = new CompositeResource(btr1, btr2)

    val block = Block(prop, prop.obsList.head, Time.minutes(61))

    comp.reserve(block, Fixture.emptyQueue) match {
      case Left(msg: Reject) => // ok
      case _                 => fail()
    }
  }
}
