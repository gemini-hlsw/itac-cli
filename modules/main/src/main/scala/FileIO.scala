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

/** Interface for some file operations, rooted at some working directory. */
trait FileIO[F[_]] {

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

}

object FileIO {

  def apply[F[_]: Sync](dir: Path, log: Logger[F]): FileIO[F] =
    new FileIO[F] {

      // This should have been checked by the caller.
      assert(dir.toFile.isDirectory, s"$dir is not a directory")

      def cwd = dir.pure[F]

      def isEmpty: F[Boolean] =
        Sync[F].delay(Option(dir.toFile.listFiles).foldMap(_.toList).isEmpty)

      def readData[A: Decoder](path: Path): F[A] = {
        val p = dir.resolve(path)
        Sync[F].delay(new String(Files.readAllBytes(p), "UTF-8")).map(parser.parse(_)).flatMap {
          case Left(e)  => Sync[F].raiseError(ItacException(e.message))
          case Right(j) => j.as[A] match {
            case Left(e) => Sync[F].raiseError(ItacException(e.message))
            case Right(a) => a.pure[F]
          }
        }
      }

      def writeData[A: Encoder](path: Path, a: A): F[Path] = {
        val p = dir.resolve(path)
        Sync[F].delay(p.toFile.isFile).flatMap {
          case true  => Sync[F].raiseError(ItacException(s"File exists: $p"))
          case false => log.trace(s"Writing $p") *>
            Sync[F].delay(Files.write(dir.resolve(path), a.asJson.asYaml.spaces2.getBytes("UTF-8")))
        }
      }

      def mkdirs(path: Path): F[Path] = {
        val p = dir.resolve(path)
        Sync[F].delay(p.toFile.isDirectory) flatMap {
          case true  => log.trace(s"Already exists: $p").as(p)
          case false => log.trace(s"Creating $p") *>
            Sync[F].delay(Files.createDirectories(dir.resolve(path)))
        }
      }

    }

}