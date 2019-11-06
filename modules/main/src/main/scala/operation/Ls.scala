package itac.operation

import cats._
import cats.effect.ExitCode
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import itac.codec.commonconfig._
import itac.data.CommonConfig
import itac.FileIO
import itac.Operation
import edu.gemini.tac.qengine.ctx.Site
import itac.ProposalLoader
import cats.effect.Sync

object Ls {

  def apply[F[_]: Sync: Parallel]: Operation[F] =
    new Operation[F] {

      def run(fileIO: FileIO[F], log: Logger[F]): F[ExitCode] = {
        for {
          cwd  <- fileIO.cwd
          conf <- fileIO.readData[CommonConfig]("common.yaml")
          pas   = conf.partners.map(p => (p.id -> p)).toMap
          when  = conf.semester.getMidpoint(Site.north).getTime // arbitrary
          ps   <- ProposalLoader[F](pas, when).loadMany(cwd.resolve("proposals").toFile)
          _    <- log.info(s"${ps.length} total.")
        } yield ExitCode.Success
      }

  }

}