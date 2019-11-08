// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import cats.effect._
import cats.implicits._
import com.monovore.decline.effect.CommandIOApp
import com.monovore.decline.Opts
import java.nio.file.Path
import java.nio.file.Paths
import com.monovore.decline.Command
import itac.operation._
import cats.data.Validated
import java.text.ParseException
import cats.data.NonEmptyList
import io.chrisdavenport.log4cats.Logger
import cats.instances.`package`.long
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import edu.gemini.tac.qengine.ctx.Semester
import scala.util.control.NonFatal
import io.circe.DecodingFailure
import io.chrisdavenport.log4cats.SelfAwareLogger

object Main extends CommandIOApp(
  name    = "itac",
  header  = "ITAC Command Line Interface"
) with MoreOpts {

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

  val ops: Opts[Operation[IO]] =
    List(init, ls).sortBy(_.name).map(Opts.subcommand(_)).foldK

  def main: Opts[IO[ExitCode]] =
    (cwd, logger[IO], ops).mapN { (cwd, log, cmd) =>
      for {
        _  <- IO(System.setProperty("edu.gemini.model.p1.schemaVersion", "2020.1.1")) // how do we figure out what to do here?
        _  <- log.debug(s"main: workspace directory is $cwd.")
        c  <- cmd.run(Workspace[IO](cwd, log), log).handleErrorWith {
                case ItacException(msg) => log.error(msg).as(ExitCode.Error)
                case NonFatal(e)        => log.error(e)(e.getMessage).as(ExitCode.Error)
              }
        _  <- log.trace(s"main: exiting with ${c.code}")
      } yield c
    }

}

trait MoreOpts {

  val cwd: Opts[Path] =
    Opts.option[Path](
      short = "d",
      long  = "dir",
      help  = "Working directory. Defaults to current directory."
    ) .withDefault(Paths.get(System.getProperty("user.dir")))
      .mapValidated { p =>
        if (p.toFile.isDirectory) p.toAbsolutePath.normalize.valid
        else s"Not a directory: $p".invalidNel
      }

  val semester: Opts[Semester] =
    Opts.argument[String]("semester")
      .mapValidated { s =>
        Validated
          .catchOnly[ParseException](Semester.parse(s))
          .leftMap(_ => NonEmptyList.of(s"Not a valid semester: $s"))
      }

  def logger[F[_]: Sync]: Opts[SelfAwareLogger[F]] =
    Opts.option[String](
      long = "verbose",
      short = "v",
      metavar = "level",
      help = "Log verbosity. One of trace debug info warn error off. Defaults to info."
    ) .withDefault("info")
      .mapValidated {
        case s @ ("trace" | "debug" | "info" | "warn" | "error" | "off") =>
          // http://www.slf4j.org/api/org/slf4j/impl/SimpleLogger.html
          System.setProperty("org.slf4j.simpleLogger.log.itac", s)
          Slf4jLogger.getLoggerFromName[F]("itac").validNel[String]
        case s => s"Invalid log level: $s".invalidNel
      }

}