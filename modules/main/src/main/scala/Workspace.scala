// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import java.nio.file.Path
import io.circe._
import io.circe.syntax._
import io.circe.yaml.parser
import io.circe.yaml.syntax._
import cats.effect.Sync
import cats.implicits._
import java.nio.file.Files
import io.chrisdavenport.log4cats.Logger
import cats.Parallel
import java.nio.file.NoSuchFileException
import io.circe.CursorOp.DownField
import itac.config.Common
import edu.gemini.tac.qengine.p1.Proposal
import itac.config.QueueConfig
import cats.effect.concurrent.Ref
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.file.Paths
import edu.gemini.spModel.core.Site

/** Interface for some Workspace operations. */
trait Workspace[F[_]] {

  def cwd: F[Path]

  /** True if the working directory is empty. */
  def isEmpty: F[Boolean]

  /** Write an encodable value to a file. */
  def writeData[A: Encoder](path: Path, a: A): F[Path]

  /** Read a decodable value from the specified file. */
  def readData[A: Decoder](path: Path): F[A]

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

}

object Workspace {

  def apply[F[_]: Sync: Parallel](dir: Path, cc: Path, log: Logger[F]): F[Workspace[F]] =
    Ref[F].of(Map.empty[Path, Any]).map { cache =>
      new Workspace[F] {

        // This should have been checked by the caller.
        assert(dir.toFile.isDirectory, s"$dir is not a directory")

        def cwd = dir.pure[F]

        def isEmpty: F[Boolean] =
          Sync[F].delay(Option(dir.toFile.listFiles).foldMap(_.toList).isEmpty)

        def readData[A: Decoder](path: Path): F[A] =
          cache.get.flatMap { map =>
            val p = dir.resolve(path)
            map.get(path) match {
              case Some(a) => log.debug(s"Getting $p from cache.").as(a.asInstanceOf[A])
              case None    => log.info(s"Reading: $p") *>
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
                }
            }
          }

        def writeData[A: Encoder](path: Path, a: A): F[Path] = {
          val p = dir.resolve(path)
          Sync[F].delay(p.toFile.isFile).flatMap {
            case true  => Sync[F].raiseError(ItacException(s"File exists: $p"))
            case false => log.info(s"Writing: $p") *>
              Sync[F].delay(Files.write(dir.resolve(path), a.asJson.asYaml.spaces2.getBytes("UTF-8")))
          }
        }

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