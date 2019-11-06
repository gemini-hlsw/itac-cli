// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.codec

import io.circe._
import edu.gemini.tac.qengine.ctx.Site

trait SiteCodec {

  implicit val EncoderSite: Encoder[Site] =
    Encoder[String].contramap(_.abbreviation())

  implicit val DecoderSite: Decoder[Site] =
    Decoder[String].emap(s => Option(Site.parse(s)).toRight(s"Could not deocde '$s' a Site."))

  implicit val KeyEncoderSite: KeyEncoder[Site] =
    KeyEncoder.instance(_.abbreviation)

  implicit val KeyDecoderSite: KeyDecoder[Site] =
    KeyDecoder.instance(s => Option(Site.parse(s)))

}

object site extends SiteCodec