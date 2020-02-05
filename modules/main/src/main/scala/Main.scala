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
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import itac.operation._
import java.nio.file.Path
import java.nio.file.Paths
import java.text.ParseException
import scala.util.control.NonFatal
import edu.gemini.tac.qengine.impl.QueueEngine
import edu.gemini.spModel.core.Semester
import edu.gemini.spModel.core.Site
import org.slf4j.impl.ColoredSimpleLogger

object Main extends CommandIOApp(
  name    = "itac",
  header  = "ITAC Command Line Interface"
) with MainOpts {

  def main: Opts[IO[ExitCode]] =
    (cwd, commonConfig, logger[IO], force, ops).mapN { (cwd, commonConfig, log, force, cmd) =>
      Blocker[IO].use { b =>
        for {
          _  <- IO(System.setProperty("edu.gemini.model.p1.schemaVersion", "2020.1.1")) // how do we figure out what to do here?
          _  <- log.debug(s"main: workspace directory is $cwd.")
          ws <- Workspace[IO](cwd, commonConfig, log, force)
          c  <- cmd.run(ws, log, b).handleErrorWith {
                  case ItacException(msg) => log.error(msg).as(ExitCode.Error)
                  case NonFatal(e)        => log.error(e)(e.getMessage).as(ExitCode.Error)
                }
          _  <- log.trace(s"main: exiting with ${c.code}")
        } yield c
      }
    }

}

// decline opts used by main, sliced off because it seems more tidy
trait MainOpts { this: CommandIOApp =>

  lazy val cwd: Opts[Path] =
    Opts.option[Path](
      short = "d",
      long  = "dir",
      help  = "Working directory. Default is current directory."
    ) .withDefault(Paths.get(System.getProperty("user.dir")))

  lazy val commonConfig: Opts[Path] =
    Opts.option[Path](
      short = "c",
      long  = "common",
      help  = "Common configuation file, relative to workspace (or absolute). Default is common.yaml"
    ).withDefault(Paths.get("common.yaml"))

  lazy val siteConfig: Opts[Path] =
    site.map {
      case Site.GN => Paths.get("gn-queue.yaml")
      case Site.GS => Paths.get("gn-queue.yaml")
    } <+> Opts.option[Path](
      short = "-c",
      long  = "config",
      help  = s"Site configuration file. Default is <site>-queue.yaml"
    )

  lazy val rolloverReport: Opts[Option[Path]] =
    Opts.option[Path](
      short = "-r",
      long  = "rollover",
      help  = s"Rollover report. Default is <site>-rollovers.yaml"
    ).orNone

  def out(default: Path): Opts[Path] =
    Opts.option[Path](
      short = "o",
      long  = "out",
      help  = s"Output file. Default is $default"
    ).withDefault(default)

  lazy val semester: Opts[Semester] =
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
          ColoredSimpleLogger.init() // sorry
          val log = new ColoredSimpleLogger("edu.gemini.itac")
          Slf4jLogger.getLoggerFromSlf4j(log).validNel[String]
        case s => s"Invalid log level: $s".invalidNel
      }

  lazy val init: Command[Operation[IO]] =
    Command(
      name   = "init",
      header = "Initialize an ITAC workspace."
    )(semester.map(Init[IO]))

  lazy val ls: Command[Operation[IO]] =
    Command(
      name   = "ls",
      header = "List proposals in the workspace."
    )(Ls[IO].pure[Opts])

  lazy val queue: Command[Operation[IO]] =
    Command(
      name   = "queue",
      header = "Generate a queue."
    )((siteConfig, rolloverReport).mapN((sc, rr) => Queue[IO](QueueEngine, sc, rr)))

  lazy val gn: Opts[Site.GN.type] = Opts.flag(
    short = "n",
    long  = "north",
    help  = Site.GN.displayName,
  ).as(Site.GN)

  lazy val gs: Opts[Site.GS.type] = Opts.flag(
    short = "s",
    long  = "south",
    help  = Site.GS.displayName,
  ).as(Site.GS)

  lazy val force: Opts[Boolean] =
    Opts.flag(
      long  = "force",
      short = "f",
      help  = "Overwrite existing files."
    ).orFalse

  lazy val site = gn <+> gs

  lazy val rollover: Command[Operation[IO]] =
    Command(
      name   = "rollover",
      header = "Generate a rollover report by fetching information from the observing database."
    )((site, Opts.option[Path](
      short = "o",
      long  = "out",
      help  = s"Output file. Default is GN-rollover.yaml or GS-rollover.yaml"
    ).orNone).mapN(Rollover(_, _)))

  lazy val ops: Opts[Operation[IO]] =
    List(init, ls, queue, rollover).sortBy(_.name).map(Opts.subcommand(_)).foldK

}