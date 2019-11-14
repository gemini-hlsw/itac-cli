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
import java.nio.file.Paths
import cats.Parallel
import java.nio.file.NoSuchFileException
import io.circe.CursorOp.DownField
import itac.config.Common
import edu.gemini.tac.qengine.p1.Proposal
import edu.gemini.tac.qengine.ctx.Site

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

  def proposals: F[List[Proposal]]

}

object Workspace {

  def apply[F[_]: Sync: Parallel](dir: Path, log: Logger[F]): Workspace[F] =
    new Workspace[F] {

      // This should have been checked by the caller.
      assert(dir.toFile.isDirectory, s"$dir is not a directory")

      def cwd = dir.pure[F]

      def isEmpty: F[Boolean] =
        Sync[F].delay(Option(dir.toFile.listFiles).foldMap(_.toList).isEmpty)

      def readData[A: Decoder](path: Path): F[A] = {
        val p = dir.resolve(path)
        log.debug(s"Reading yaml $p") *>
        Sync[F].delay(new String(Files.readAllBytes(p), "UTF-8")).map(parser.parse(_)).flatMap[A] {
          case Left(e)  => Sync[F].raiseError(ItacException(s"Failure reading $p\n$e.message"))
          case Right(j) => j.as[A] match {
            case Left(e) => Sync[F].raiseError(e)
            case Right(a) => a.pure[F]
          }
        } .onError {
          case f: DecodingFailure =>
            ItacException(s"Failure reading $p\n  ${f.message}\n    at ${f.history.collect { case DownField(k) => k } mkString("/")}")
              .raiseError[F, Unit]
        }
      }

      def writeData[A: Encoder](path: Path, a: A): F[Path] = {
        val p = dir.resolve(path)
        Sync[F].delay(p.toFile.isFile).flatMap {
          case true  => Sync[F].raiseError(ItacException(s"File exists: $p"))
          case false => log.debug(s"Writing yaml $p") *>
            Sync[F].delay(Files.write(dir.resolve(path), a.asJson.asYaml.spaces2.getBytes("UTF-8")))
        }
      }

      def mkdirs(path: Path): F[Path] = {
        val p = dir.resolve(path)
        Sync[F].delay(p.toFile.isDirectory) flatMap {
          case true  => log.debug(s"Already exists: $p").as(p)
          case false => log.debug(s"Creating $p") *>
            Sync[F].delay(Files.createDirectories(dir.resolve(path)))
        }
      }

      def commonConfig: F[Common] =
        readData[Common](Paths.get("common.yaml")).recoverWith {
          case _: NoSuchFileException => cwd.flatMap(p => Sync[F].raiseError(ItacException(s"Not an ITAC Workspace: $p")))
        }

      def proposals: F[List[Proposal]] =
        for {
          cwd  <- cwd
          conf <- commonConfig
          p     = cwd.resolve("proposals")
          pas   = conf.engine.partners.map { p => (p.id, p) } .toMap
          when  = conf.semester.getMidpoint(Site.north).getTime // arbitrary
          _    <- log.debug(s"Reading proposals from $p")
          ps   <- ProposalLoader[F](pas, when).loadMany(p.toFile)
          _    <- ps.traverse { case (f, Left(es)) => log.warn(s"$f: ${es.toList.mkString(", ")}") ; case _ => ().pure[F] }
        } yield ps.collect {
          case (_, Right(ps)) => ps.toList
        } .flatten


    }

}