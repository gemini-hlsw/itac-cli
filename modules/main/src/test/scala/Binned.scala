// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package test

import itac.config.Binned
import cats.tests.CatsSuite
import org.scalatest.matchers.should.Matchers
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.{ arbitrary => arb }
import org.scalacheck.Gen
import edu.gemini.tac.qengine.util.Angle
import scala.collection.immutable.TreeMap

class BinnedSuite extends CatsSuite with Matchers {

  test("negative size") {
    val res = Binned(10, -1, 0, TreeMap.empty)
    res shouldBe Left(s"Binning range [10, -1) is non-positive.")
  }

  test("zero size") {
    val res = Binned(1, 1, 0, TreeMap.empty)
    res shouldBe Left(s"Binning range [1, 1) is non-positive.")
  }

  test("negative bin count") {
    val res = Binned(0, 24, -1, TreeMap.empty)
    res shouldBe Left("Bin count -1 must be positive and evenly divisible by total size 24.")
  }

  test("zero bin count") {
    val res = Binned(0, 24, 0, TreeMap.empty)
    res shouldBe Left("Bin count 0 must be positive and evenly divisible by total size 24.")
  }

  test("non-divisible bin count") {
    val res = Binned(0, 24, 5, TreeMap.empty)
    res shouldBe Left("Bin count 5 must be positive and evenly divisible by total size 24.")
  }

  test("bad keys") {
    val res = Binned(0, 24, 8, TreeMap(-3 -> "x", 0 -> "a", 3 -> "b", 4 -> "c", 9 -> "d", 24 -> "z", 27 -> "zz"))
    res shouldBe Left("Bin keys must be multiples of 3 in [0, 24). Invalid keys found: -3 4 24 27")
  }

  test("ok") {
    val res = Binned(0, 24, 8, TreeMap(0 -> "a", 3 -> "b", 6 -> "c", 9 -> "d", 21 -> "z"))
    res.isRight shouldBe true
  }

  def genBinned[A: Arbitrary](min: Int, max: Int, bins: Int): Gen[Binned[A]] =
    Gen.listOfN(bins, arb[(A, Boolean)]).map { vs =>
      val binSize = (max - min) / bins
      val values  = TreeMap(vs.zipWithIndex.collect { case ((v, true), n) => ((min + n * binSize) -> v) } : _*)
      Binned(min, max, bins, values) match {
        case Left(e)  => fail(e)
        case Right(b) => b
      }
    }

  test("toRaBinGroup (bad)") {
    val res = Binned[Int](0, 20, 2, TreeMap.empty).flatMap(_.engine.toRaBinGroup(0))
    res shouldBe Left("RA bin group must have range [0, 24) ... found [0, 20)")
  }

  test("toRaBinGroup (ok)") {
    forAll(genBinned[Int](0, 24, 12)) { b =>
      b.engine.toRaBinGroup(0) match {
        case Left(e)    => fail(e)
        case Right(rbg) =>
          (0 until 24) foreach { h =>
            b.find(h).getOrElse(0) shouldBe rbg(new Angle(h.toDouble, Angle.Hr))
          }
        }
    }
  }

  test("toRaBinGroup (find equivalence)") {
    forAll(genBinned[Byte](0, 24, 8).flatMap(b => Arbitrary.arbitrary[Byte].map(s => (s, b)))) { case (s, b) =>
      val rbg = b.engine.toRaBinGroup(0).toOption.get
      val k = s.toInt.abs % 24
      b.find(k).getOrElse(0) shouldBe rbg.apply(k * 60)
    }
  }

  test("toDecBinGroup (bad)") {
    val res = Binned[Short](0, 20, 2, TreeMap.empty).flatMap(_.engine.toDecBinGroup(0))
    res shouldBe Left("Declination bin group must have range [-90, 90) ... found [0, 20)")
  }

  test("toDecBinGroup (ok)") {
    forAll(genBinned[Short](-90, 90, 18)) { b =>
      b.engine.toDecBinGroup(0) match {
        case Left(e)    => fail(e)
        case Right(dbg) =>
          (-90 until 90 by 10) foreach { a =>
            dbg.get(new Angle(a.toDouble, Angle.Deg)).map(_.binValue) shouldBe Some(b.values.getOrElse(a, 0))
          }
        }
    }
  }

}

