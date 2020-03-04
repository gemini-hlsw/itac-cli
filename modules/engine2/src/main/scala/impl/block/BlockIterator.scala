package edu.gemini.tac.qengine.impl.block

import edu.gemini.tac.qengine.util.Time

import BlockIterator.IMap
import edu.gemini.tac.qengine.api.queue.time.PartnerTime
import edu.gemini.tac.qengine.ctx.Partner
import edu.gemini.tac.qengine.p1.{Observation, Proposal}

/**
 * An immutable iterator that can be used to generate time blocks across all
 * partners and proposals.  It combines single partner iterators, slicing the
 * time across partners according to a provide sequence and map of partner
 * time quanta.
 */
trait BlockIterator {
  private val LOGGER : org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  val allPartners: List[Partner]

  /**
   * Maps a Partner to the length of its time quantum.
   */
  val quantaMap: PartnerTime

  /**
   * The remaining sequence of Partner time quanta.  As the iterator progresses,
   * the sequence advances.
   */
  val seq: Seq[Partner]

  /**
   * Map of Partner to PartnerTimeBlockIterator.  As the iterator progresses,
   * the PartnerTimeBlockIterators are advanced.
   */
  val iterMap: IMap

  /**
   * The time remaining in the current time quantum.
   */
  val remTime: Time

  /**
   * Computes the list of remaining proposals in the iterator.
   */
  def remPropList: List[Proposal] =
    allPartners.flatMap(p => iterMap(p).remainingProposals)

  /**
   *  The partner that occupies the current time quantum.
   */
  def currentPartner: Partner = seq.head

  def isStartOf(prop: Proposal): Boolean =
    (currentPartner == prop.ntac.partner) &&
    iterMap(currentPartner).isStartOf(prop)

  /**
   * Whether this iterator will produce any time blocks.  This method should
   * be called before calling any other methods.  If it returns
   * <code>false</code>, the result of using the other methods is not
   * specified.
   */
  def hasNext: Boolean = iterMap(currentPartner).hasNext

  /**
   * Generates the next TimeBlock and a new iterator configured to produce the
   * remaining blocks.
   */
  def next(activeList : Proposal=>List[Observation]) : (Block, BlockIterator) = {
    // Advance the partner time block iterator.  Use at most remTime time.
    // This may cause the iterator to generate a block for part of an
    // observation, which is fine.
    val (block, iter) = iterMap(currentPartner).next(remTime, activeList)

    // Return the block and advance this iterator.  This may move to another
    // observation in the same time quantum or, if we've reached the end of the
    // quantum, it will advance to the next partner in the sequence.  Use the
    // updated map of partner time block iterators.
    (block, advance(block.time, iterMap.updated(currentPartner, iter)))
  }

  /**
   * Extracts all the TimeBlocks from this iterator into a single list.
   * This method is mostly intended for testing support since it is not
   * tail recursive and could be expensive for lengthy sequences.
   */
  def toList(activeList : Proposal=>List[Observation]) : List[Block] =
    if (!hasNext) Nil else next(activeList) match { case (b, it) => b :: it.toList(activeList) }

  /**
   * Skips the proposal that would be generated in the next TimeBlock.
   */
  def skip(activeList : Proposal=>List[Observation]): BlockIterator = {
    val partnerIter = iterMap(currentPartner).skip(activeList)
    val m = iterMap.updated(currentPartner, partnerIter)
    if (partnerIter.hasNext) mkIterator(seq, remTime, m) else advancePartner(m)
  }

  private def advance(t: Time, m: IMap): BlockIterator =
    if ((remTime > t) && m(currentPartner).hasNext) mkIterator(seq, remTime - t, m)
    else advancePartner(m)

  private def advancePartner(m: IMap): BlockIterator =
    advancePartner(seq.tail, m)

  private def advancePartner(s: Seq[Partner], blockIteratorByPartner: IMap, remaining: Set[Partner] = BlockIterator.validpartners(allPartners, quantaMap)): BlockIterator = {
    if (remaining.isEmpty || s.isEmpty){
      //QueueCalculationLog.logger.log(Level.INFO, "BlockIterator.empty()")
      LOGGER.debug(<Event source="BlockIterator" event="Empty"/>.toString())
      new BlockIterator.Empty(allPartners)
    } else {
      val hasNext1 = blockIteratorByPartner(s.head).hasNext
      val hasQuantaTime = !quantaMap(s.head).isZero
      if (hasNext1 && hasQuantaTime) {
        mkIterator(s, quantaMap(s.head), blockIteratorByPartner)
      } else {
        val moreSeq = s.tail
        moreSeq.isEmpty match {
          case true => LOGGER.debug("End of sequence")
          case false => {
            val nextPartner = moreSeq.head
            LOGGER.debug(<Event source="BlockIterator" event="advancePartner">
              {nextPartner.fullName}
            </Event>.toString)
          }
        }
        //QueueCalculationLog.logger.log(Level.INFO, (<Event source="BlockIterator" event="advancePartner">{s.head.fullName}</Event>).toString)
        advancePartner(moreSeq, blockIteratorByPartner, remaining - s.head)
      }
    }
  }

  protected def mkIterator(partnerSeq: Seq[Partner], t: Time, iterMap: IMap): BlockIterator
}

object BlockIterator {
  type IMap = Map[Partner, PartnerBlockIterator]

  private class Empty(val allPartners: List[Partner]) extends BlockIterator {
    val quantaMap: PartnerTime = PartnerTime.empty(allPartners)
    val seq: Seq[Partner] = Seq.empty
    val remTime: Time = Time.Zero
    val iterMap: IMap = Map.empty

    override def isStartOf(prop: Proposal): Boolean = false
    override def remPropList: List[Proposal] = Nil
    override def hasNext: Boolean = false
    def mkIterator(s: Seq[Partner], t: Time, m: IMap) = this
  }

  private class BlockIteratorImpl(
          val allPartners: List[Partner],
          val quantaMap: PartnerTime,
          val seq: Seq[Partner],
          val remTime: Time,
          val iterMap: IMap) extends BlockIterator {

    def mkIterator(s: Seq[Partner], t: Time, m: IMap) = {
      org.slf4j.LoggerFactory.getLogger(this.getClass).debug("BlockIterator: " + seq.head + " remTime " + remTime)
      //QueueCalculationLog.logger.log(Level.INFO, (<Event source="BlockIterator" event="mkIterator">{s.head.fullName}</Event>).toString)
      new BlockIteratorImpl(allPartners, quantaMap, s, t, m)
    }
  }

  private def genIterMap(allPartners: List[Partner], m: Map[Partner, List[Proposal]], activeList : Proposal=>List[Observation]): IMap =
    Partner.mkMap(allPartners, m, Nil).mapValues(PartnerBlockIterator.apply(_, activeList))

  // Finds the first partner that has a non-zero time quantum and a proposal
  // list and returns the sequence advanced to that partner and the time in its
  // time quantum.
  private def init(qMap: PartnerTime, iMap: IMap, partnerSeq: Seq[Partner], remaining: Set[Partner]): (Seq[Partner], Time) =
    if (remaining.isEmpty || partnerSeq.isEmpty)
      (Seq.empty, Time.Zero)
    else if (!qMap(partnerSeq.head).isZero && iMap(partnerSeq.head).hasNext)
      (partnerSeq, qMap(partnerSeq.head))
    else
      init(qMap, iMap, partnerSeq.tail, remaining - partnerSeq.head)

  // Calculates an initial set of valid partners.  It trims any partners
  // without a time quanta.  These partners should not appear in the sequence.
  private def validpartners(allPartners: List[Partner], quantaMap: PartnerTime): Set[Partner] =
    allPartners.filter(!quantaMap(_).isZero).toSet

  /**
   * Constructs the TimeBlockIterator for the appropriate queue band category,
   * using the time quanta indicated in the quantaMap, the sorted proposals in
   * the propLists, and the provided sequence of partners.
   *
   * <p>The partner sequence can be finite but an infinite sequence is expected
   * in order to be able to generate time blocks for all the proposals.
   */
  def apply(allPartners: List[Partner], quantaMap: PartnerTime, seq: Seq[Partner], propLists: Map[Partner, List[Proposal]], activeList : Proposal=>List[Observation]): BlockIterator = {
    val iterMap = genIterMap(allPartners, propLists, activeList)

    init(quantaMap, iterMap, seq, validpartners(allPartners, quantaMap)) match {
      case (s, t) if s.isEmpty => new Empty(allPartners)
      case (partnerSeq, remainingTime) => {
        new BlockIteratorImpl(allPartners, quantaMap, partnerSeq, remainingTime, iterMap)
      }
    }
  }
}
