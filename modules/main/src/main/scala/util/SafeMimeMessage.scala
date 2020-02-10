package itac.util

import javax.mail.Session
import javax.mail.Message.RecipientType
import javax.mail.internet.{ InternetAddress, MimeMessage }

/** MimeMessage builder. */
case class SafeMimeMessage(
  from:    InternetAddress,
  to:      List[InternetAddress],
  cc:      List[InternetAddress],
  subject: String,
  content: String
) {

  /** Converts to a MimeMessage to be sent via a Java Mail API Transport. */
  def toMimeMessage(smtpHost: String): MimeMessage = {

    val props = {
      val ps = new java.util.Properties
      ps.put("mail.transport.protocol", "smtp")
      ps.put("mail.smtp.host", smtpHost)
      ps
    }

    val mm = new MimeMessage(Session.getInstance(props, null))
    mm.setFrom(from)
    to.foreach(mm.addRecipient(RecipientType.TO, _))
    cc.foreach(mm.addRecipient(RecipientType.CC, _))
    mm.setSubject(subject)
    mm.setContent(content, "text/plain")
    mm

  }

}
