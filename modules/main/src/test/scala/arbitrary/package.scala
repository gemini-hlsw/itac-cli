// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package test

package object arbitrary {

  object all
    extends PercentArbitrary
       with PartnerArbitrary
       with LocalDateArbitrary

}
