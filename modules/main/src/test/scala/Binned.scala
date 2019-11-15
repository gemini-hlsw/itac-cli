// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package test

import itac.config.Binned
import cats.tests.CatsSuite
import org.scalatest.matchers.should.Matchers

class BinnedSuite extends CatsSuite with Matchers {

  test("negative size") {
    val res = Binned(10, -1, 0, Map.empty)
    res shouldBe Left(s"Binning range [10, -1) is non-positive.")
  }

  test("zero size") {
    val res = Binned(1, 1, 0, Map.empty)
    res shouldBe Left(s"Binning range [1, 1) is non-positive.")
  }

  test("negative bin count") {
    val res = Binned(0, 24, -1, Map.empty)
    res shouldBe Left("Bin count -1 must be positive and evenly divisible by total size 24.")
  }

  test("zero bin count") {
    val res = Binned(0, 24, 0, Map.empty)
    res shouldBe Left("Bin count 0 must be positive and evenly divisible by total size 24.")
  }

  test("non-divisible bin count") {
    val res = Binned(0, 24, 5, Map.empty)
    res shouldBe Left("Bin count 5 must be positive and evenly divisible by total size 24.")
  }

  test("bad keys") {
    val res = Binned(0, 24, 8, Map(-3 -> "x", 0 -> "a", 3 -> "b", 4 -> "c", 9 -> "d", 24 -> "z", 27 -> "zz"))
    res shouldBe Left("Bin keys be multiples of 3 in [0, 24). Invalid keys found: -3 4 24 27")
  }

  test("ok") {
    val res = Binned(0, 24, 8, Map(0 -> "a", 3 -> "b", 6 -> "c", 9 -> "d", 21 -> "z"))
    res.isRight shouldBe true
  }

}

