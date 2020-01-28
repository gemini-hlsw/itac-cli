package edu.gemini.tac.qengine.log

import edu.gemini.tac.qengine.p1.{QueueBand, Proposal}
import QueueBand.{Category => TimeCat}
import collection.SortedSet
import edu.gemini.tac.qengine.log.ProposalLog.Key

/**
 * A collection of log messages keyed by a combination of proposal id and
 * queue band time category.  The idea is that there may be multiple log
 * messages for a particular proposal, one for each stage of the queue
 * calculation.
 *
 * <p>This class wraps a SortedMap in order to simplify/hide the
 * (Proposal.Id, QueueBand.TimeCategory) keys and add a few features specific
 * to proposal logging.
 */
trait ProposalLog {
  import ProposalLog.Entry
  import ProposalLog.removeDuplicateKeys

  protected val log: List[Entry]

  def isDefinedAt(key: Key): Boolean = log.exists(_.key == key)
  def isDefinedAt(id: Proposal.Id, cat: TimeCat): Boolean =
    isDefinedAt(Key(id, cat))

  /**
   * Obtains the LogMessage associated with the given Key, or throws an
   * exception if not defined.  Use the get method if the key is possibly not
   * defined.
   */
  def apply(key: Key): LogMessage = get(key).get

  /**
   * Obtains the LogMessage associated with the given proposal id and queue
   * band time category, or throws an exception if not defined.  This is
   * a convenience method to hide the creation of the Key from the proposal
   * id and time category.
   */
  def apply(id: Proposal.Id, cat: TimeCat): LogMessage = get(Key(id, cat)).get

  def get(key: Key): Option[LogMessage] =
    log.find(_.key == key).map(_.msg)

  def get(id: Proposal.Id, cat: TimeCat): Option[LogMessage] = get(Key(id, cat))

  def getOrElse(key: Key, default: LogMessage): LogMessage =
    get(key).getOrElse(default)

  def getOrElse(id: Proposal.Id, cat: TimeCat, default: LogMessage): LogMessage =
    getOrElse(Key(id, cat), default)

  /**
   * Converts the log into a sorted list of Key and LogMessage where the Key
   * contains the proposal id and the time category.
   */
  def toMap: Map[Key, LogMessage] = log.reverse.map(_.toPair).toMap

  /**
   * Converts the log into a sorted list of entries that is ordered
   * by insertion order of the log messages.  Intermediate log messages are
   * included in the results, so a given (proposal id, time category) pair
   * may appear multiple times.
   */
  def toDetailList: List[Entry] = log.reverse

  /**
   * Gets a list of ProposalLog Entry, ordered by insertion, in which only the
   * final log message for a particular (proposal id, time category) key is
   * kept.  This is useful for displaying results to the user since it shows
   * the final order of events and hides any backtracking done by the algorithm.
   * In the full list, a given proposal may be accepted, backtracked over, and
   * later rejected.  This is likely to confuse the user.  Instead, with the
   * filtered list, they will see only the final rejection.
   */
  def toList: List[Entry] = removeDuplicateKeys(log)

  /**
   * Gets a list of (QueueBand.TimeCategory, LogMessage) pairs for all entries
   * related to the given proposal.
   */
  def toList(id: Proposal.Id): List[Entry] =
    removeDuplicateKeys(log.filter(_.key.id == id))

  /**
   * Gets all the proposal ids for proposals that have one or more log messages
   * in the ProposalLog.
   */
  def proposalIds: SortedSet[Proposal.Id] =
    log.foldLeft(SortedSet.empty[Proposal.Id]) {
      (s,e) => s + e.key.id
    }

  /**
   * Creates a new ProposalLog with the LogMessage associated with the given
   * key added or replaced with the provided message.
   */
  def updated(key: Key, msg: LogMessage): ProposalLog = mkProposalLog(Entry(key, msg) :: log)

  /**
   * Creates a new ProposalLog with the LogMessage associated with the given
   * proposal id and time category added or replaced with the provided
   * message.  This is a convenience method to obviate the need to explicitly
   * create a Key object from the proposal id and category.
   */
  def updated(id: Proposal.Id, cat: QueueBand.Category, msg: LogMessage): ProposalLog =
    updated(Key(id, cat), msg)

  /**
   * Creates a new ProposalLog with entries for all the given proposals at the
   * specified time category.  The LogMessages are determined by the provided
   * function.
   */
  def updated(propList: List[Proposal], cat: TimeCat, f: Proposal => LogMessage): ProposalLog =
    mkProposalLog(propList.foldLeft(log) {
      (lst, prop) => Entry(Key(prop.id, cat), f(prop)) :: lst
    })

  /**
   * Creates a proposal log from a sorted map.
   */
  protected def mkProposalLog(l: List[Entry]): ProposalLog

  def toXML =
    <ProposalLog>
      { log.reverse.map(_.msg.toXML) }
    </ProposalLog>
}

object ProposalLog {
  /**
   * A combination of proposal id and queue band time category that serves as
   * a key for looking up proposal log messages.
   */
  case class Key(id: Proposal.Id, cat: TimeCat) extends Ordered[Key] {
    def compare(that: Key): Int = id.compare(that.id) match {
      case 0 => cat.compare(that.cat)
      case n => n
    }
  }

  /**
   * ProposalLog entry, which groups a key (proposal id, time category) and
   * LogMessage.
   */
  case class Entry(key: Key, msg: LogMessage) {
    def toPair: Tuple2[Key, LogMessage] = (key, msg)
  }

  /**
   * Proposal entry ordering by Key.
   */
  object KeyOrdering extends Ordering[Entry] {
    def compare(e1: Entry, e2: Entry): Int = e1.key.compare(e2.key)
  }

  // Takes a reversed list of entries, filters it to remove duplicate keys
  // that represent intermediate log entries, reversing the list to insertion
  // order in the process.
  private def removeDuplicateKeys(reverseLst: List[Entry]): List[Entry] = {
    case class Res(lst: List[Entry], keys: Set[Key])
    val empty = Res(Nil, Set.empty)

    // Note: reverses the log entries as it goes leaving them in insertion
    // order and selecting only the final entry per key (which is the first
    // one to show up in the reversed list).
    reverseLst.foldLeft(empty) {
      (res, entry) =>
        if (res.keys.contains(entry.key))
          res // skip this one
        else

          Res(entry :: res.lst, res.keys + entry.key)
    }.lst
  }

  val Empty: ProposalLog = new ProposalLogImpl(List.empty)

  def apply(tups: (Proposal.Id, TimeCat, LogMessage)*): ProposalLog =
    new ProposalLogImpl(List(tups.map {
      case (id, cat, msg) => Entry(Key(id, cat), msg)
    }: _*).reverse)

  private class ProposalLogImpl(val log: List[Entry]) extends ProposalLog {
    protected def mkProposalLog(l: List[Entry]): ProposalLog = new ProposalLogImpl(l)
  }


}
