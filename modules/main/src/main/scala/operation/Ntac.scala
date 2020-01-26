// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.operation

import cats.effect.ExitCode
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.circe._
import io.circe.syntax._
import itac.codec.ntac._
import itac.Operation
import itac.Workspace
import java.nio.file.Path
import cats.FlatMap

object Ntac {

  def apply[F[_]: FlatMap](out: Path): Operation[F] =
    new Operation[F] {

      def run(ws: Workspace[F], log: Logger[F]): F[ExitCode] =
        for {
          ps <- ws.proposals
          j   = Json.obj("proposals" -> ps.map(_.ntac).asJson)
          _  <- ws.writeData(out, j)
        } yield ExitCode.Success

  }

}