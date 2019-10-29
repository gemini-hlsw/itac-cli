package edu.gemini.tac.qengine.impl.resource

import org.junit._
import Assert._
import edu.gemini.tac.qengine.p1._
import edu.gemini.tac.qengine.api.config.TimeRestriction
import edu.gemini.tac.qengine.util.{Percent, Time}
import edu.gemini.tac.qengine.p1.CloudCover.CCAny
import edu.gemini.tac.qengine.p1.ImageQuality.IQAny
import edu.gemini.tac.qengine.p1.SkyBackground.SBAny
import edu.gemini.tac.qengine.p1.WaterVapor
import edu.gemini.tac.qengine.p1.WaterVapor._
import edu.gemini.tac.qengine.impl.block.Block
import edu.gemini.tac.qengine.log.RejectRestrictedBin
import edu.gemini.tac.qengine.ctx.Site

class TimeResourceTest {
  import edu.gemini.tac.qengine.ctx.TestPartners._
  val partners = All

  private val ntac   = Ntac(US, "x", 0, Time.hours(10))
  private val target = Target(0.0, 0.0) // not used
  private def conds(wv: WaterVapor) =
    ObsConditions(CCAny, IQAny, SBAny, wv)

  private val bin = TimeRestriction("WV", Percent(10)) {
    (_, obs, _) => obs.conditions.wv <= WV50
  }

  // 10% of 10 hours = 1 hr = 60 min
  private val res60min = TimeResource(bin, Time.hours(10))

  private def mkProp(wv: WaterVapor): Proposal =
    CoreProposal(ntac, site = Site.south, obsList = List(Observation(target, conds(wv), Time.hours(10))))

  @Test def testReserveNoMatch() {
    val prop = mkProp(WV80)

    // If the restriction doesn't match the block, then the same instance is
    // returned -- not a copy with the same values
    val block = Block(prop, prop.obsList.head, Time.hours(1))
    res60min.reserve(block, Fixture.emptyQueue) match {
      case Right(res) => assertSame(res60min, res)
      case _ => fail()
    }
  }

  @Test def testReserveNoTime() {
    val prop = mkProp(WV20)

    // Here the restriction matches the block, but we're not reserving any
    // time.  Again, no copy should be made
    val block = Block(prop, prop.obsList.head, Time.hours(0))
    res60min.reserve(block, Fixture.emptyQueue) match {
      case Right(res) => assertSame(res60min, res)
      case _ => fail()
    }
  }

  @Test def testReserve() {
    val prop = mkProp(WV20)

    // Reserve 15 of the 60 available minutes
    val block = Block(prop, prop.obsList.head, Time.minutes(15))
    res60min.reserve(block, Fixture.emptyQueue) match {
      case Right(res) => assertEquals(Time.minutes(45), res.remaining)
      case _ => fail()
    }
  }

  @Test def testReject() {
    val prop = mkProp(WV20)

    // Try to reserve more than 1 hour
    val block = Block(prop, prop.obsList.head, Time.minutes(61))
    res60min.reserve(block, Fixture.emptyQueue) match {
      case Left(msg: RejectRestrictedBin) => assertEquals(prop, msg.prop)
      case _ => fail()
    }
  }
}
