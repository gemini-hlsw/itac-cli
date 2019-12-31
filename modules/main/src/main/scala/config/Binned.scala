// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.config

import cats.implicits._
import io.circe._
import io.circe.syntax._
import edu.gemini.tac.qengine.api.config.RaBinGroup
import edu.gemini.tac.qengine.api.config.DecBinGroup
import scala.collection.immutable.TreeMap

/**
 * A map from keys in `min until max by binSize` to values of type `A`, where `min < max` and
 * `binSize % (max - min) = 0`.
 */
sealed trait Binned[A] { outer =>
  def min: Int
  def max: Int
  def size: Int = max - min
  def bins: Int // invariant: size % bins = 0
  def binSize: Int = size / bins
  def values: TreeMap[Int, A] // invariant: key % binSize = 0

  def map[B](f: A => B): Binned[B] =
    new Binned[B] {
      def min = outer.min
      def max = outer.max
      def bins = outer.bins
      def values = outer.values.map { case (k, v) => (k, f(v)) }
    }

  def find(key: Int): Option[A] =
    values.get(key / binSize)

  object engine {

    def toRaBinGroup(zero: A): Either[String, RaBinGroup[A]] =
      if (min != 0 || max != 24) Left(s"RA bin group must have range [0, 24) ... found [$min, $max)")
      else Right(RaBinGroup((0 until bins).map(values.getOrElse(_, zero))))

    def toDecBinGroup(zero: A): Either[String, DecBinGroup[A]] =
      if (min != -90 || max != 90) Left(s"Declination bin group must have range [-90, 90) ... found [$min, $max)")
      else Right(DecBinGroup((-90 until 90 by binSize).map(values.getOrElse(_, zero))))

  }

  final override def toString =
    s"Binned($min to $max by $binSize, values = ${(min until max by binSize).map(n => values.getOrElse(n, "*")).mkString(", ")})"

}


object Binned {

  def apply[A](min: Int, max: Int, bins: Int, values: TreeMap[Int, A]): Either[String, Binned[A]] = {
    val size = max - min
         if (size <= 0) Left(s"Binning range [$min, $max) is non-positive.")
    else if (bins <= 0 || size % bins != 0) Left(s"Bin count $bins must be positive and evenly divisible by total size $size.")
    else {
      val binSize = size / bins
      val badKeys = values.keys.toList.filterNot(k => (k - min) % binSize == 0 && k >= min && k < max)
      if (badKeys.nonEmpty) Left(s"Bin keys must be multiples of $binSize in [$min, $max). Invalid keys found: ${badKeys.sorted.mkString(" ")}")
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
      apply(min, max, bins, TreeMap(values.toList: _*))
    }

  implicit def encoder[A: Encoder]: Encoder[Binned[A]] =
    Encoder { b =>
      Json.obj(
        "bins"   -> b.bins.asJson,
        "values" -> b.values.asJson
      )
    }

}