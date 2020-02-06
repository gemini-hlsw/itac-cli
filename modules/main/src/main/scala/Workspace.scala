// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import cats.effect.concurrent.Ref
import cats.effect.Sync
import cats.implicits._
import cats.Parallel
import edu.gemini.spModel.core.Site
import edu.gemini.tac.qengine.p1.Proposal
import edu.gemini.tac.qengine.p2.rollover.RolloverReport
import io.chrisdavenport.log4cats.Logger
import io.circe.{ Encoder, Decoder, DecodingFailure }
import io.circe.yaml.Printer
import io.circe.CursorOp.DownField
import io.circe.syntax._
import io.circe.yaml.parser
import itac.codec.rolloverreport._
import itac.config.Common
import itac.config.QueueConfig
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import cats.effect.Resource
import java.nio.file.StandardCopyOption

/** Interface for some Workspace operations. */
trait Workspace[F[_]] {

  def cwd: F[Path]

  /** True if the working directory is empty. */
  def isEmpty: F[Boolean]

  /** Write an encodable value to a file. Header must be a YAML comment. */
  def writeData[A: Encoder](path: Path, a: A, header: String = ""): F[Path]

  /** Read a decodable value from the specified file. */
  def readData[A: Decoder](path: Path): F[A]

  def extractResource(name: String, path: Path): F[Path]

  /**
   * Create a directory relative to `cwd`, including any intermediate ones, returning the path of
   * the created directory.
   */
  def mkdirs(path: Path): F[Path]

  def commonConfig: F[Common]

  def queueConfig(path: Path): F[QueueConfig]

  def proposals: F[List[Proposal]]

  /**
   * Create a directory under `cwd` with a name like GN-20190524-103322 and return its path
   * relative to the workspace root.
   */
  def newQueueFolder(site: Site): F[Path]

  def writeRolloveReport(path: Path, rr: RolloverReport): F[Path]

  def readRolloverReport(path: Path): F[RolloverReport]
  def writeText(path: Path, text: String): F[Path]

}

object Workspace {

  val printer: Printer =
    Printer(
      preserveOrder = true,
      // dropNullKeys = false,
      // indent = 2,
      maxScalarWidth = 120,
      // splitLines = true,
      // indicatorIndent = 0,
      // tags = Map.empty,
      // sequenceStyle = FlowStyle.Block,
      // mappingStyle = FlowStyle.Block,
      // stringStyle = StringStyle.Plain,
      // lineBreak = LineBreak.Unix,
      // explicitStart = false,
      // explicitEnd = false,
      // version = YamlVersion.Auto
    )

  def apply[F[_]: Sync: Parallel](dir: Path, cc: Path, log: Logger[F], force: Boolean): F[Workspace[F]] =
    ItacException(s"Workspace directory not found: $dir").raiseError[F, Workspace[F]].unlessA(dir.toFile.isDirectory) *>
    Ref[F].of(Map.empty[Path, Any]).map { cache =>
      new Workspace[F] {

        def cwd = dir.pure[F]

        def isEmpty: F[Boolean] =
          Sync[F].delay(Option(dir.toFile.listFiles).foldMap(_.toList).isEmpty)

        def readData[A: Decoder](path: Path): F[A] =
          cache.get.flatMap { map =>
            val p = dir.resolve(path)
            map.get(path) match {
              case Some(a) => log.debug(s"Getting $p from cache.").as(a.asInstanceOf[A])
              case None    => log.debug(s"Reading: $p") *>
                Sync[F].delay(new String(Files.readAllBytes(p), "UTF-8")).map(parser.parse(_)).flatMap[A] {
                  case Left(e)  => Sync[F].raiseError(ItacException(s"Failure reading $p\n$e.message"))
                  case Right(j) => j.as[A] match {
                    case Left(e)  => Sync[F].raiseError(e)
                    case Right(a) => cache.set(map + (path -> a)).as(a)
                  }
                } .onError {
                  case f: DecodingFailure =>
                    ItacException(s"Failure reading $p\n  ${f.message}\n    at ${f.history.collect { case DownField(k) => k } mkString("/")}")
                      .raiseError[F, Unit]
                  case _: NoSuchFileException =>
                    ItacException(s"No such file: $path").raiseError[F, Unit]
                }
            }
          }

        def extractResource(name: String, path: Path): F[Path] =
          Resource.make(Sync[F].delay(getClass.getResourceAsStream(name)))(is => Sync[F].delay(is.close()))
            .use { is =>
              val p = dir.resolve(path)
              Sync[F].delay(p.toFile.isFile).flatMap {
                case false | `force` => log.info(s"Writing: $p") *>
                  Sync[F].delay(Files.copy(is, p, StandardCopyOption.REPLACE_EXISTING)).as(p)
                case true  => Sync[F].raiseError(ItacException(s"File exists: $p"))
              }
            }

        def writeText(path: Path, text: String): F[Path] = {
          val p = dir.resolve(path)
          Sync[F].delay(p.toFile.isFile).flatMap {
            case false | `force` => log.info(s"Writing: $p") *>
              Sync[F].delay(Files.write(dir.resolve(path), text.getBytes("UTF-8")))
            case true  => Sync[F].raiseError(ItacException(s"File exists: $p"))
          }
        }

        def writeData[A: Encoder](path: Path, a: A, header: String = ""): F[Path] =
          writeText(path, header + printer.pretty(a.asJson))

        def writeRolloveReport(path: Path, rr: RolloverReport): F[Path] = {
          // The user cares about when report was generated in local time, so that's what we will
          // use in the header comment.
          val ldt = ZonedDateTime.ofInstant(rr.timestamp, ZoneId.systemDefault)
          val fmt = DateTimeFormatter.ofPattern("yyyy-dd-MM HH:mm 'local time' (z)")
          val header =
            s"""|
                |# This is the ${rr.semester} rollover report for ${rr.site.displayName}, generated at ${fmt.format(ldt)}.
                |# It is ok to edit this file as long as the format is preserved, and it is ok to add comment lines.
                |
                |""".stripMargin
          writeData(path, rr, header)
        }

        def readRolloverReport(path: Path): F[RolloverReport] =
          commonConfig.flatMap(cc => readData(path)(decoderRolloverReport(cc.engine.partners)))

        def mkdirs(path: Path): F[Path] = {
          val p = dir.resolve(path)
          Sync[F].delay(p.toFile.isDirectory) flatMap {
            case true  => log.debug(s"Already exists: $path").as(p)
            case false => log.info(s"Creating folder: $path") *>
              Sync[F].delay(Files.createDirectories(dir.resolve(path)))
          }
        }

        def commonConfig: F[Common] =
          readData[Common](cc).recoverWith {
            case _: NoSuchFileException => cwd.flatMap(p => Sync[F].raiseError(ItacException(s"Not an ITAC Workspace: $p")))
          }

        def queueConfig(path: Path): F[QueueConfig] =
          readData[QueueConfig](path).recoverWith {
            case _: NoSuchFileException => cwd.flatMap(p => Sync[F].raiseError(ItacException(s"Site-specific configuration file not found: ${p.resolve(path)}")))
          }

        def proposals: F[List[Proposal]] =
          for {
            cwd  <- cwd
            conf <- commonConfig
            p     = cwd.resolve("proposals")
            pas   = conf.engine.partners.map { p => (p.id, p) } .toMap
            when  = conf.semester.getMidpointDate(Site.GN).getTime // arbitrary
            _    <- log.info(s"Reading proposals from $p")
            ps   <- ProposalLoader[F](pas, when).loadMany(p.toFile)
            _    <- ps.traverse { case (f, Left(es)) => log.warn(s"$f: ${es.toList.mkString(", ")}") ; case _ => ().pure[F] }
            psʹ   = ps.collect { case (_, Right(ps)) => ps.toList } .flatten
            _    <- log.info(s"Read ${ps.length} proposals.")
          } yield ps.collect { case (_, Right(ps)) => ps.toList } .flatten

        def newQueueFolder(site: Site): F[Path] =
          for {
            dt <- Sync[F].delay(LocalDateTime.now)
            f   = DateTimeFormatter.ofPattern("yyyyMMdd-HHmms")
            n   = s"${site.abbreviation}-${f.format(dt)}"
            p   = Paths.get(n)
            _  <- mkdirs(p)
          } yield p

      }
    }

}