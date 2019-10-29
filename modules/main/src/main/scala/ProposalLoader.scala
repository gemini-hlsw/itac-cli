// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.itac

import cats.Parallel
import cats.effect.Sync
import cats.implicits._
import edu.gemini.model.p1.{mutable => M}
import edu.gemini.model.p1.immutable.Proposal
import java.io.File
import java.lang.reflect.Constructor
import javax.xml.bind.JAXBContext

object ProposalLoader {

  // Private members here are a performance hack until https://github.com/gemini-hlsw/ocs/pull/1722
  // shows up in published library code, at which point most of this goes away and we can deletage
  // to edu.gemini.model.p1.immutable.ProposalIO

  private val context: JAXBContext = {
    val factory        = new M.ObjectFactory
    val contextPackage = factory.getClass.getName.reverse.dropWhile(_ != '.').drop(1).reverse
    JAXBContext.newInstance(contextPackage, getClass.getClassLoader)
  }

  private val ctor: Constructor[Proposal] =
    classOf[Proposal].getConstructor(classOf[M.Proposal])

  private def unsafeLoad(file: File): Proposal =
    ctor.newInstance(context.createUnmarshaller.unmarshal(file))

  def load[F[_]: Sync](file: File): F[Proposal] =
    Sync[F].delay(unsafeLoad(file))

  def loadMany[F[_]: Sync: Parallel](dir: File): F[List[Proposal]] =
    Sync[F].delay(Option(dir.listFiles)).flatMap {
      case Some(arr) => arr.toList.parTraverse(load(_))
      case None      => Sync[F].raiseError(new RuntimeException(s"Not a directory: $dir"))
    }

}

