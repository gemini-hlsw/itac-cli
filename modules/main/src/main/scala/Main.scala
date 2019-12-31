// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import cats.data.NonEmptyList
import cats.data.Validated
import cats.effect._
import cats.implicits._
import com.monovore.decline.Command
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.Opts
import edu.gemini.tac.qengine.ctx.Semester
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import itac.operation._
import java.nio.file.Path
import java.nio.file.Paths
import java.text.ParseException
import scala.util.control.NonFatal
import edu.gemini.tac.qengine.impl.QueueEngine

object Main extends CommandIOApp(
  name    = "itac",
  header  = "ITAC Command Line Interface"
) with MainOpts {

  def main: Opts[IO[ExitCode]] =
    (cwd, logger[IO], ops).mapN { (cwd, log, cmd) =>
      for {
        _  <- IO(System.setProperty("edu.gemini.model.p1.schemaVersion", "2020.1.1")) // how do we figure out what to do here?
        _  <- log.debug(s"main: workspace directory is $cwd.")
        ws <- Workspace[IO](cwd, log)
        c  <- cmd.run(ws, log).handleErrorWith {
                case ItacException(msg) => log.error(msg).as(ExitCode.Error)
                case NonFatal(e)        => log.error(e)(e.getMessage).as(ExitCode.Error)
              }
        _  <- log.trace(s"main: exiting with ${c.code}")
      } yield c
    }

}

// decline opts used by main, sliced off because it seems more tidy
trait MainOpts { this: CommandIOApp =>

  val cwd: Opts[Path] =
    Opts.option[Path](
      short = "d",
      long  = "dir",
      help  = "Working directory. Defaults to current directory."
    ) .withDefault(Paths.get(System.getProperty("user.dir")))
      .mapValidated { p =>
        // `isDirectory` is a side-effect, but we're only doing it once so we'll pretend it's not
        val pʹ = p.toAbsolutePath.normalize
        if (pʹ.toFile.isDirectory) pʹ.valid
        else s"Not a directory: $pʹ".invalidNel
      }

  val semester: Opts[Semester] =
    Opts.argument[String]("semester")
      .mapValidated { s =>
        Validated
          .catchOnly[ParseException](Semester.parse(s))
          .leftMap(_ => NonEmptyList.of(s"Not a valid semester: $s"))
      }

  def logger[F[_]: Sync]: Opts[Logger[F]] =
    Opts.option[String](
      long    = "verbose",
      short   = "v",
      metavar = "level",
      help    = "Log verbosity. One of: trace debug info warn error off. Defaults to info."
    ) .withDefault("info")
      .mapValidated {
        case s @ ("trace" | "debug" | "info" | "warn" | "error" | "off") =>
          // http://www.slf4j.org/api/org/slf4j/impl/SimpleLogger.html
          System.setProperty("org.slf4j.simpleLogger.log.edu", s)
          Slf4jLogger.getLoggerFromName[F]("edu.gemini.itac").validNel[String]
        case s => s"Invalid log level: $s".invalidNel
      }

  val init: Command[Operation[IO]] =
    Command(
      name   = "init",
      header = "Initialize an ITAC workspace."
    )(semester.map(Init[IO]))

  val ls: Command[Operation[IO]] =
    Command(
      name   = "ls",
      header = "List proposals in the workspace."
    )(Opts.unit.as(Ls[IO]))

  val queue: Command[Operation[IO]] =
    Command(
      name   = "queue",
      header  = "Generate a queue."
    )(Opts.unit.as(Queue[IO](QueueEngine)))

  val ops: Opts[Operation[IO]] =
    List(init, ls, queue).sortBy(_.name).map(Opts.subcommand(_)).foldK

}