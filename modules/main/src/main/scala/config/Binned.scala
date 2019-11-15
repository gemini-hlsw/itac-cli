// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.config

import cats.implicits._
import io.circe._
import io.circe.syntax._

sealed trait Binned[A] {
  def min: Int
  def max: Int
  def size: Int = max - min
  def bins: Int // invariant: size % bins = 0
  def binSize: Int = size / bins
  def values: Map[Int, A] // invariant: key % binSize = 0
}

object Binned {

  def apply[A](min: Int, max: Int, bins: Int, values: Map[Int, A]): Either[String, Binned[A]] = {
    val size = max - min
         if (size <= 0) Left(s"Binning range [$min, $max) is non-positive.")
    else if (bins <= 0 || size % bins != 0) Left(s"Bin count $bins must be positive and evenly divisible by total size $size.")
    else {
      val binSize = size / bins
      val badKeys = values.keys.toList.filterNot(k => k % binSize == 0 && k >= min && k < max)
      if (badKeys.nonEmpty) Left(s"Bin keys be multiples of $binSize in [$min, $max). Invalid keys found: ${badKeys.sorted.mkString(" ")}")
      else Right {
        val (min0, max0, bins0, values0) = (min, max, bins, values)
        new Binned[A] {
          val min    = min0
          val max    = max0
          val bins   = bins0
          val values = values0
        }
      }
    }
  }

  def decoder[A: Decoder](min: Int, max: Int): Decoder[Binned[A]] =
    Decoder { c =>
      c.downField("bins").as[Int] product c.downField("values").as[Map[Int, A]]
    } .emap { case (bins, values) =>
      apply(min, max, bins, values)
    }

  implicit def encoder[A: Encoder]: Encoder[Binned[A]] =
    Encoder { b =>
      Json.obj(
        "bins"   -> b.bins.asJson,
        "values" -> b.values.asJson
      )
    }

}