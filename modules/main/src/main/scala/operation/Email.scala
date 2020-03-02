// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package itac.operation

import cats.implicits._
import itac.config.QueueConfig
import edu.gemini.tac.qengine.p1.Proposal
import edu.gemini.tac.qengine.p1.Mode
import edu.gemini.tac.qengine.p1.CoreProposal
import edu.gemini.tac.qengine.p1._
import org.apache.velocity.VelocityContext
import java.io.StringWriter
import scala.collection.JavaConverters._
import cats.effect.Sync
import cats.Parallel
import itac.Operation
import cats.effect.{Blocker, ExitCode}
import _root_.io.chrisdavenport.log4cats.Logger
import itac.Workspace
import java.nio.file.Path
import edu.gemini.spModel.core.Site
import itac.EmailTemplateRef
import edu.gemini.spModel.core.Semester
import org.apache.velocity.app.VelocityEngine
import edu.gemini.tac.qengine.api.QueueEngine

/**
 * @see Velocity documentation https://velocity.apache.org/engine/2.2/developer-guide.html
 */
object Email {

  // This one's easiest if we uncurry everything.
  def apply[F[_]: Sync: Parallel](
    qe:             QueueEngine,
    siteConfig:     Path,
    rolloverReport: Option[Path]
  ): Operation[F] =
    new AbstractQueueOperation[F](qe, siteConfig, rolloverReport) {

      // The entry
      def run(ws: Workspace[F], log: Logger[F], b: Blocker): F[ExitCode] = {

        // Velocity engine, which takes a bit of work to set up but the computation as a whole is pure.
        val velocity: VelocityEngine = {

          // I feel bad about this but it's the best I could figure out. I need the underlying
          // side-effecting logger from `log` so I can make stupid Velocity use it, but there's no
          // direct accessor. So we will try to pull it out and if it fails we get the normal logger
          // and well, too bad that's what we get. The underlying class is probably
          // io.chrisdavenport.log4cats.slf4j.internal.Slf4jLoggerInternal.Slf4jLogger, which has a
          // `logger` member (and generated accessor `logger()`). In the future maybe someone will
          // use something else, in which case they'll find this comment and work something out.
          val sideEffectingLogger: Option[org.slf4j.Logger] =
            Either.catchNonFatal {
              val getter     = log.getClass.getDeclaredMethod("logger")
              val underlying = getter.invoke(log).asInstanceOf[org.slf4j.Logger]
              underlying
            } .toOption

          val ve = new VelocityEngine
          ve.setProperty("runtime.strict_math",        true)
          ve.setProperty("runtime.strict_mode.enable", true)
          sideEffectingLogger.foreach(ve.setProperty("runtime.log.instance", _))
          ve

        }

        for {
          qc <- ws.queueConfig(siteConfig)
          ps <- ws.proposals
          _  <- createSuccessfulEmailsForClassical(velocity, ws, ps, qc)
          // TODO: more!
        } yield ExitCode.Success

      }

      // For now
      type MailMessage = String

      /** Some convenience operations for filtering a list of proposals. */
      implicit class ProposalListOps(ps: List[Proposal]) {
        def classicalProposalsForSite(site: Site): List[Proposal] =
          ps.filter { p =>
              p.site == site           &&
              p.mode == Mode.Classical &&
            !p.isJointComponent
          }
      }

      def createPiEmail(velocity: VelocityEngine,ws: Workspace[F], p: Proposal): F[MailMessage] = {
        // We have no types to ensure these things, so let's assert to be sure.
        assert(!p.isJointComponent, "Program must not be a joint component.")
        // TODO: assert that p is successful
        for {
          s  <- ws.commonConfig.map(_.semester)
          t  <- ws.readEmailTemplate(EmailTemplateRef.PiSuccessful)
          ps  = velocityBindings(p, s)
          tit = merge(velocity, t.name, t.titleTemplate, ps)
          _  <- Sync[F].delay(println(tit))
          bod = merge(velocity, t.name, t.bodyTemplate, ps)
          _  <- Sync[F].delay(println(bod))
        } yield "ok"
      }

      def createNgoEmails(p: Proposal): F[List[MailMessage]] = {
        p match {
          case c: CoreProposal      => List(s"<email for ${c.ntac.partner.id}>")
          case j: JointProposal     => j.ntacs.map(n =>s"<email for ${n.partner.id}>")
          case _: JointProposalPart => Nil // Don't create mails for joint parts
        }
      } .pure[F]

      def createSuccessfulEmailsForClassical(velocity: VelocityEngine, ws: Workspace[F], ps: List[Proposal], qc: QueueConfig): F[List[MailMessage]] =
        ps.classicalProposalsForSite(qc.site).parFlatTraverse { cp =>
          (createPiEmail(velocity, ws, cp), createNgoEmails(cp)).parMapN(_ :: _)
        }

      /**
       * Given a Velocity template and a map of bindings, evaluate the template and return the
       * generated text, or an indication of why it failed.
       */
      def merge(velocity: VelocityEngine, templateName: String, template: String, bindings: Map[String, AnyRef]): Either[Throwable, String] =
        Either.catchNonFatal {
          val ctx = new VelocityContext(bindings.asJava)
          val out = new StringWriter
          if (!velocity.evaluate(ctx, out, templateName, template)) {
            // It's not at all clear when we get `false` here rather than a thrown exception. It
            // has never come up in testing. But this is here just in case.
            throw new RuntimeException("Velocity evaluation failed (see the log, or re-run with -v debug if there is none!).")
          }
          out.toString
        }

      /**
       * Construct a map of key/value pairs that will be bound to the Velocity context. Our strategy
       * is to define only the keys where values are available (rather than using `null`) and then
       * running in strict reference mode. What this means is, undefined references will throw an
       * exception as Satan intended. Templates can use `#if` to determine whether a key is defined or
       * not, before attempting a dereference.
       * @see strict reference mode https://velocity.apache.org/engine/1.7/user-guide.html#strict-reference-mode
       */
      def velocityBindings(p: Proposal, s: Semester): Map[String, AnyRef] = {

        var mut = scala.collection.mutable.Map.empty[String, AnyRef]
        mut = mut // defeat bogus unused warning

        // bindings that are always present
        mut += "country"             -> p.ntac.partner.fullName
        mut += "ntacRecommendedTime" -> p.ntac.awardedTime.toHours.toString
        mut += "ntacRefNumber"       -> p.ntac.reference
        mut += "ntacRanking"         -> p.ntac.ranking.format
        mut += "semester"            -> s.toString

        // bindings that may be missing
        p.ntac.comment.foreach(v => mut += "itacComments" -> v)
        p.ntac.comment.foreach(v => mut += "ntacComment"  -> v)
        p.piEmail     .foreach(v => mut += "piMail"       -> v)
        p.piName      .foreach(v => mut += "piName"       -> v)
        p.p1proposal  .foreach(p => mut += "progTitle"    -> p.title)

        // Not implemented yet
        // mut += "geminiComment"       -> null
        // mut += "geminiContactEmail"  -> null
        // mut += "geminiId"            -> null
        // mut += "jointInfo"           -> null
        // mut += "jointTimeContribs"   -> null
        // mut += "ntacSupportEmail"    -> null // need to add to Partner based on c
        // mut += "progId"              -> null
        // mut += "progKey"             -> null
        // mut += "queueBand"           -> null
        // mut += "timeAwarded"         -> null

        // Done
        mut.toMap

      }

    }

}