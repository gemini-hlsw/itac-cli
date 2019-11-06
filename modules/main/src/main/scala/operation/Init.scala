package itac.operation

import cats._
import cats.effect.ExitCode
import cats.implicits._
import edu.gemini.tac.qengine.ctx.Semester
import io.chrisdavenport.log4cats.Logger
import itac.codec.commonconfig._
import itac.data.CommonConfig
import itac.FileIO
import itac.Operation
import java.nio.file.Paths

object Init {

  def apply[F[_]: Monad](semester: Semester): Operation[F] =
    new Operation[F] {

      def run(fileIO: FileIO[F], log: Logger[F]): F[ExitCode] = {

        val initLog = log.withModifiedString("init: " + _)

        def init: F[ExitCode] =
          for {
            _ <- List("proposals", "emails").traverse(fileIO.mkdirs(_))
            _ <- fileIO.writeData(Paths.get("common.yaml"), CommonConfig.default(semester))
          } yield ExitCode.Success

        for {
          _   <- initLog.trace(s"semester is $semester")
          cwd <- fileIO.cwd
          ok  <- fileIO.isEmpty
          ec  <- if (ok) init <* initLog.info(s"initialized ITAC workspace in $cwd")
                 else initLog.error(s"working directory is not empty: $cwd").as(ExitCode.Error)
        } yield ec

      }

  }

}