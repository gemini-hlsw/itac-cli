// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.codec

import io.circe._
import io.circe.generic.semiauto._
import edu.gemini.tac.qengine.ctx.Partner

trait PartnerCodec {
  import percent._
  import site._

  implicit val EncoderPartner: Encoder[Partner] = deriveEncoder
  implicit val DecoderPartner: Decoder[Partner] = deriveDecoder

}

object partner extends PartnerCodec