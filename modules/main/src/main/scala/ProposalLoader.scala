// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac

import cats._
import cats.data._
import cats.effect.Sync
import cats.implicits._
import edu.gemini.model.p1.{mutable => M, immutable => I}
import java.io.File
import java.lang.reflect.Constructor
import javax.xml.bind.JAXBContext
import edu.gemini.tac.qengine.p1.Proposal
import edu.gemini.tac.qengine.p1.io.ProposalIo
import edu.gemini.tac.qengine.p1.io.JointIdGen
import edu.gemini.tac.qengine.ctx.Partner

trait ProposalLoader[F[_]] {

  def load(file: File): StateT[F, JointIdGen, EitherNel[String, NonEmptyList[Proposal]]]

  // problem here is all the errors are smashed together

  def loadMany(file: File): StateT[F, JointIdGen, List[EitherNel[String, NonEmptyList[Proposal]]]]

}

object ProposalLoader {

  // Private members here are a performance hack until https://github.com/gemini-hlsw/ocs/pull/1722
  // shows up in published library code, at which point most of this goes away and we can deletage
  // to edu.gemini.model.p1.immutable.ProposalIO

  private val context: JAXBContext = {
    val factory        = new M.ObjectFactory
    val contextPackage = factory.getClass.getName.reverse.dropWhile(_ != '.').drop(1).reverse
    JAXBContext.newInstance(contextPackage, getClass.getClassLoader)
  }

  private val ctor: Constructor[I.Proposal] =
    classOf[I.Proposal].getConstructor(classOf[M.Proposal])

  def apply[F[_]: Sync: Parallel](
    partners: Map[String, Partner],
    when: Long
  ): ProposalLoader[F] =
    new ProposalLoader[F] {

      private def unsafeLoadPhase1(file: File): I.Proposal =
        ctor.newInstance(context.createUnmarshaller.unmarshal(file))

      def loadPhase1(file: File): F[I.Proposal] =
        Sync[F].delay(unsafeLoadPhase1(file))

      def loadManyPhase1(dir: File): F[List[I.Proposal]] =
        Sync[F].delay(Option(dir.listFiles)).flatMap {
          case Some(arr) => arr.toList.parTraverse(loadPhase1(_))
          case None      => Sync[F].raiseError(new RuntimeException(s"Not a directory: $dir"))
        }

      val pio: ProposalIo =
        new ProposalIo(partners)

      def read(proposal: I.Proposal): State[JointIdGen, EitherNel[String, NonEmptyList[Proposal]]] =
        State { jig =>
          pio.read(proposal, when, jig) match {
            case scalaz.Failure(ss)         => (jig,  NonEmptyList(ss.head, ss.tail.toList).asLeft)
            case scalaz.Success((ps, jigʹ)) => (jigʹ, NonEmptyList(ps.head, ps.tail.toList).asRight)
          }
        }

      def load(file: File): StateT[F, JointIdGen, EitherNel[String, NonEmptyList[Proposal]]] =
        StateT { jig => loadPhase1(file).map(read(_).run(jig).value) }

      def loadMany(dir: File): StateT[F, JointIdGen, List[EitherNel[String, NonEmptyList[Proposal]]]] =
        StateT { jig => loadManyPhase1(dir).map(_.traverse(read(_)).run(jig).value) }

    }

}




