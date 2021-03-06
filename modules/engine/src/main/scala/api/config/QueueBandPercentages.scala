// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.tac.qengine.api.config

import edu.gemini.tac.qengine.util.Percent
import edu.gemini.tac.qengine.p1.QueueBand

/**
 * Queue band percentages, the percent of total queue time to assign to each
 * band.  Defaults to 30/30/40.
 */
final case class QueueBandPercentages(
  band1: Percent = Default.Band1Percent,
  band2: Percent = Default.Band2Percent,
  band3: Percent = Default.Band3Percent
) {

  /**
   * Determines what percent of the total queue is designated for the
   * specified band.
   */
  val bandPercent: Map[QueueBand, Percent] = (QueueBand.values zip List(
    band1,
    band2,
    band3,
    Percent.Hundred - (band1 + band2 + band3)
  )).toMap

  require(bandPercent.values forall { perc =>
    (perc >= Percent.Zero) && (perc <= Percent.Hundred)
  })

  /**
   * The percentage of the queue associated with the given QueueBand Category.
   */
  def categoryPercent(cat: QueueBand.Category): Percent =
    QueueBand.values.filter(_.categories.contains(cat)).foldLeft(Percent.Zero) { (perc, band) =>
      perc + bandPercent(band)
    }

  override def toString: String =
    s"(B1=${band1.value.toInt}%, B2=${band2.value.toInt}%, B3=${band3.value.toInt}%)"

  def apply(band: QueueBand): Percent         = bandPercent(band)
  def apply(cat: QueueBand.Category): Percent = categoryPercent(cat)
}

object QueueBandPercentages {
  def apply() = new QueueBandPercentages()

  def apply(band1: Int, band2: Int, band3: Int) =
    new QueueBandPercentages(
      Percent(band1.toDouble),
      Percent(band2.toDouble),
      Percent(band3.toDouble)
    )
}
