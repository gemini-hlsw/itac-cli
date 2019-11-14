package itac.operation

import cats._
import cats.effect.ExitCode
import cats.implicits._
import edu.gemini.tac.qengine.ctx.Semester
import io.chrisdavenport.log4cats.Logger
import itac.config.Common
import itac.Workspace
import itac.Operation
import java.nio.file.Paths

object Init {

  def apply[F[_]: Monad](semester: Semester): Operation[F] =
    new Operation[F] {

      def run(ws: Workspace[F], log: Logger[F]): F[ExitCode] = {

        val initLog = log.withModifiedString("init: " + _)

        def init: F[ExitCode] =
          for {
            _ <- List("proposals", "emails").traverse(ws.mkdirs(_))
            _ <- ws.writeData(Paths.get("common.yaml"), Common.dummy(semester))
          } yield ExitCode.Success

        for {
          _   <- initLog.trace(s"semester is $semester")
          cwd <- ws.cwd
          ok  <- ws.isEmpty
          ec  <- if (ok) init <* initLog.info(s"initialized ITAC workspace in $cwd")
                 else initLog.error(s"working directory is not empty: $cwd").as(ExitCode.Error)
        } yield ec

      }

  }

}