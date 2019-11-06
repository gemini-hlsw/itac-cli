// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

package object codec {

  object all
    extends PartnerCodec
       with PercentCodec
       with SiteCodec
       with SemesterCodec
       with LocalDateRangeCodec
       with CommonConfigCodec

}
