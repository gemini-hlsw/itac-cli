package itac

sealed abstract case class EmailTemplate(filename: String) {
  // we "just know" that this is where the template file lives on the classpath
  val resourcePath = s"/email_templates/$filename"
}

object EmailTemplate {

  object NgoClassical        extends EmailTemplate("ngo_classical.vm")
  object NgoExchange         extends EmailTemplate("ngo_exchange.vm")
  object NgoJointClassical   extends EmailTemplate("ngo_joint_classical.vm")
  object NgoJointExchange    extends EmailTemplate("ngo_joint_exchange.vm")
  object NgoJointPoorWeather extends EmailTemplate("ngo_joint_poor_weather.vm")
  object NgoJointQueue       extends EmailTemplate("ngo_joint_queue.vm")
  object NgoPoorWeather      extends EmailTemplate("ngo_poor_weather.vm")
  object NgoQueue            extends EmailTemplate("ngo_queue.vm")
  object PiSuccessful        extends EmailTemplate("pi_successful.vm")
  object Unsuccessful        extends EmailTemplate("unsuccessful.vm")

  val all: List[EmailTemplate] =
    List(
      NgoClassical,
      NgoExchange,
      NgoJointClassical,
      NgoJointExchange,
      NgoJointPoorWeather,
      NgoJointQueue,
      NgoPoorWeather,
      NgoQueue,
      PiSuccessful,
      Unsuccessful,
    )

}