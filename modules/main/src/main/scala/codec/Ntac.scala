// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.codec

import cats.implicits._
import io.circe._
import io.circe.syntax._
import edu.gemini.tac.qengine.p1.Ntac
import edu.gemini.tac.qengine.util.Time

trait NtacCodec {

  implicit val NtacEncoder: Encoder[Ntac] =
    Encoder { ntac =>
      if (ntac.awardedTime == Time.ZeroHours)
        Json.obj(
          "id" -> ntac.reference.asJson
        )
      else
        Json.obj(
          "id" -> ntac.reference.asJson,
          "accept"        -> Json.obj(
            "hours"       -> ntac.awardedTime.toHours.value.asJson,
            "minHours"    -> "TBD".asJson,
            "ranking"     -> ntac.ranking.num.orEmpty.asJson,
            "poorWeather" -> ntac.poorWeather.asJson
          )
        )
    }

}

object ntac extends NtacCodec