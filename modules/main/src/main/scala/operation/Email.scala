package itac.operation

import cats.implicits._
import itac.config.QueueConfig
import edu.gemini.tac.qengine.p1.Proposal
import edu.gemini.tac.qengine.p1.Mode
import edu.gemini.tac.qengine.p1.CoreProposal
import edu.gemini.tac.qengine.p1._
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
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
import itac.EmailTemplate
import edu.gemini.spModel.core.Semester

object Email {

  type MailMessage = String

  private implicit class ProposalListOps(ps: List[Proposal]) {
    def classicalProposalsForSite(site: Site): List[Proposal] =
      ps.filter { p =>
          p.site == site           &&
          p.mode == Mode.Classical &&
        !p.isJointComponent
      }
  }

  private implicit class WorkspaceOps[F[_]: Sync: Parallel](ws: Workspace[F]) {

        def createPiEmail(p: Proposal): F[MailMessage] = {
        // // merging of proposals will merge PI emails to semi-colon separated list of emails
        // final PhaseIProposal phaseIProposal = proposal.getPhaseIProposal();
        // String recipients = phaseIProposal.getInvestigators().getPi().getEmail();
        // Submission submission = phaseIProposal.getPrimary();
        // VariableValues values = new VariableValues(proposal, banding, submission, successful);

        // final List<String> ccRecipients = extractCcRecipientsFromProposal(proposal, phaseIProposal);

        // return createEmail(queue, banding, proposal, template, values, recipients, StringUtils.join(ccRecipients,"; "));

        for {
          s  <- ws.commonConfig.map(_.semester)
          t  <- ws.readEmailTemplate(EmailTemplate.PiSuccessful)
          ps  = velocityProperties(p, s)
          out = merge(t, ps)
          _  <- Sync[F].delay(println(out))
        } yield "ok"

        // p.piEmail.getOrElse("??? unknown email!").pure[F]
      }

      def createNgoEmails(p: Proposal): List[MailMessage] =
        p match {
          case c: CoreProposal      => List(s"<email for ${c.ntac.partner.id}>")
          case j: JointProposal     => j.ntacs.map(n =>s"<email for ${n.partner.id}>")
          case _: JointProposalPart => Nil // Don't create mails for joint parts
        }

      def createSuccessfulEmailsForClassical(ps: List[Proposal], qc: QueueConfig): F[List[MailMessage]] =
        ps.classicalProposalsForSite(qc.site).flatTraverse { cp =>
          createPiEmail(cp).map(_ :: createNgoEmails(cp))
        }

  }

  def apply[F[_]: Sync: Parallel](
    siteConfig: Path,
  ): Operation[F] =
    new Operation[F] {


      def run(ws: Workspace[F], log: Logger[F], b: Blocker): F[ExitCode] =
        for {
          qc <- ws.queueConfig(siteConfig)
          ps <- ws.proposals
          mm <- ws.createSuccessfulEmailsForClassical(ps, qc)
          _  <- Sync[F].delay(mm.foreach(println))
        } yield ExitCode.Success

    }


  // def createPiEmail(p: Proposal): MailMessage = {
  //   // // merging of proposals will merge PI emails to semi-colon separated list of emails
  //   // final PhaseIProposal phaseIProposal = proposal.getPhaseIProposal();
  //   // String recipients = phaseIProposal.getInvestigators().getPi().getEmail();
  //   // Submission submission = phaseIProposal.getPrimary();
  //   // VariableValues values = new VariableValues(proposal, banding, submission, successful);

  //   // final List<String> ccRecipients = extractCcRecipientsFromProposal(proposal, phaseIProposal);

  //   // return createEmail(queue, banding, proposal, template, values, recipients, StringUtils.join(ccRecipients,"; "));
  //   ???
  // }

  // def createNgoEmails(p: Proposal): List[MailMessage] =
  //   p match {
  //     case _: CoreProposal      => Nil
  //     case _: JointProposal     => Nil
  //     case _: JointProposalPart => Nil // Don't create mails for joint parts
  //   }

  // def createSuccessfulEmailsForClassical(ps: List[Proposal], qc: QueueConfig): List[MailMessage] =
  //   ps.filter { p =>
  //     p.site == qc.site        &&
  //     p.mode == Mode.Classical &&
  //    !p.isJointComponent
  //   } .flatMap { cp =>
  //     createPiEmail(cp) :: createNgoEmails(cp)
  //   }

  // def createSuccessfulEmailsForBanded: List[MailMessage] = {
  //   Nil
  // }

  // def createUnsuccessfulEmails: List[MailMessage] = {
  //   Nil
  // }

  // def createEmails(ps: List[Proposal], qc: QueueConfig): List[MailMessage] = {
  //   createSuccessfulEmailsForClassical(ps, qc) ++
  //   createSuccessfulEmailsForBanded    ++
  //   createUnsuccessfulEmails
  // }

  def merge(template: String, properties: Map[String, AnyRef]): Either[String, String] =
    Either.catchNonFatal {

      // Our context
      val ctx = new VelocityContext(properties.asJava)

      // Output writer
      val out = new StringWriter

      // Evaluate the template
      if (!Velocity.evaluate(ctx, out, "itac", template))
        throw new RuntimeException("Velocity evaluation failed for some reason.")

      // Done
      out.toString

    } .leftMap(_.getMessage)


  /**
   * Construct a map of key/value pairs that will be bound to the Velocity context. Our strategy
   * is to define only the keys where values are available (rather than using `null`) and then
   * running in strict reference mode. What this means is, undefined references will throw an
   * exception as Satan intended. Templates can use `#if` to determine whether a key is defined or
   * not, before attempting a dereference.
   * @see strict reference mode https://velocity.apache.org/engine/1.7/user-guide.html#strict-reference-mode
   */
  def velocityProperties(p: Proposal, s: Semester): Map[String, AnyRef] = {

    var mut = scala.collection.mutable.Map.empty[String, AnyRef]
    mut = mut // defeat bogus unused warning

    // properties that are always present
    mut += "country"             -> p.ntac.partner.fullName
    mut += "ntacRecommendedTime" -> p.ntac.awardedTime.toHours.toString
    mut += "ntacRefNumber"       -> p.ntac.reference
    mut += "ntacRanking"         -> p.ntac.ranking.format
    mut += "semester"            -> s.toString

    // properties that may be missing
    p.ntac.comment.foreach(v => mut += "itacComments" -> v)
    p.ntac.comment.foreach(v => mut += "ntacComment"  -> v)
    p.piEmail     .foreach(v => mut += "piMail"       -> v)
    p.piName      .foreach(v => mut += "piName"       -> v)

    // Not implemented yet
    // mut += "geminiComment"       -> null
    // mut += "geminiContactEmail"  -> null
    // mut += "geminiId"            -> null
    // mut += "jointInfo"           -> null
    // mut += "jointTimeContribs"   -> null
    // mut += "ntacSupportEmail"    -> null // need to add to Partner based on c
    // mut += "progId"              -> null
    // mut += "progTitle"           -> null
    // mut += "progKey"             -> null
    // mut += "queueBand"           -> null
    // mut += "timeAwarded"         -> null

    // Done
    mut.toMap

  }


      // this.geminiComment = itac.getGeminiComment() != null ? itac.getGeminiComment() : "";
      // this.itacComments =  itac.getComment() != null ? itac.getComment() : "";
      // if (itac.getRejected() || itac.getAccept() == null) {
      //     // either rejected or no accept part yet: set empty values
      //     this.progId = "";
      //     this.progKey = "";
      //     this.geminiContactEmail = "";
      //     this.timeAwarded = "";
      // } else {
      //     this.progId = itac.getAccept().getProgramId();
      //     this.progKey = ProgIdHash.pass(this.progId);
      //     this.geminiContactEmail = itac.getAccept().getContact();
      //     this.timeAwarded = itac.getAccept().getAward().toPrettyString();
      // }

      // if (!successful) {
      //     // ITAC-70: use original partner time, the partner time might have been edited by ITAC to "optimize" queue
      //     this.timeAwarded = "0.0 " + ntacExtension.getRequest().getTime().getUnits();
      // }

      // if (proposal.isJoint()) {
      //     StringBuffer info = new StringBuffer();
      //     StringBuffer time = new StringBuffer();
      //     for(Submission submission : doc.getSubmissions()){
      //         NgoSubmission ngoSubmission = (NgoSubmission) submission;
      //         info.append(ngoSubmission.getPartner().getName());
      //         info.append(" ");
      //         info.append(ngoSubmission.getReceipt().getReceiptId());
      //         info.append(" ");
      //         info.append(ngoSubmission.getPartner().getNgoFeedbackEmail()); //TODO: Confirm -- not sure
      //         info.append("\n");
      //         time.append(ngoSubmission.getPartner().getName() + ": " + ngoSubmission.getAccept().getRecommend().toPrettyString());
      //         time.append("\n");
      //     }
      //     this.jointInfo = info.toString();
      //     this.jointTimeContribs = time.toString();
      // }

      // // ITAC-70 & ITAC-583: use original recommended time, the awarded time might have been edited by ITAC to optimize queue
      // TimeAmount time = ntacExtension.getAccept().getRecommend();
      // this.country = ntacExtension.getPartner().getName();
      // this.ntacComment = ntacExtension.getComment() != null ? ntacExtension.getComment() : "";
      // this.ntacRanking = ntacExtension.getAccept().getRanking().toString();
      // this.ntacRecommendedTime = time.toPrettyString();
      // this.ntacRefNumber = ntacExtension.getReceipt().getReceiptId();
      // this.ntacSupportEmail = ntacExtension.getAccept().getEmail();

      // // Merging of PIs: first names and last names will be concatenated separated by '/',
      // // emails will be concatenated to a list separated by semi-colons
      // this.piMail = pi.getEmail();
      // this.piName = pi.getFirstName() + " " + pi.getLastName();
      // if (doc.getTitle() != null) {
      //     this.progTitle = doc.getTitle();
      // }

      // if (banding != null) {
      //     this.queueBand = banding.getBand().getDescription();
      // } else if (proposal.isClassical()) {
      //     this.queueBand = "classical";
      // } else {
      //     this.queueBand = N_A;
      // }


}
