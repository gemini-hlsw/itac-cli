package itac

import io.chrisdavenport.log4cats.Logger
import cats.effect.ExitCode

trait Operation[F[_]] {
  def run(ws: Workspace[F], log: Logger[F]): F[ExitCode]
}
