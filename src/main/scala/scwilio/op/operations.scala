package scwilio
package op

import xml._

/**
 * Trait for all Twilio REST operation. Each operation is responsible
 * for creating the Twilio HTTP Request and parsing the results (if the
 * operation was successful).
 */
trait TwilioOperation[R] {
  /**
   * Creates the HTTP op.
   */
  def request(conf: HttpConfig): dispatch.Request

  def parser: NodeSeq => R
}

/**
 * Implicit conversions to ease Twilio XML parsing.
 */
protected object XmlPredef {

  /**
   * Let a NodeSeq's text be converted to a Option[String]. Returns None if the
   * text is empty.
   */
  implicit def nodeSeq2StringOption(nodes: NodeSeq) : Option[String] = nodes.text match {
    case "" => None
    case s: String => Some(s)
  }

  /**
   * Let a NodeSeq's text be converted to a String. Returns an empty String if the
   * text is empty.
   */
  implicit def nodeSeq2String(nodes: NodeSeq) : String = nodes.text

}

/**
 *   Defines Twilio REST operations.
 */
case class ListAvailableNumbers(countryCode: String) extends TwilioOperation[Seq[Phonenumber]] {
  def request(conf: HttpConfig) = conf.API_BASE / "AvailablePhoneNumbers" / countryCode / "Local"

  def parser = parse _

  def parse(nodes: NodeSeq) = {
    for (num <- nodes \\ "AvailablePhoneNumber" \ "PhoneNumber")
    yield {
      Phonenumber(num.text)
    }
  }
}

case class DialOperation(
                          from: Phonenumber,
                          to: Phonenumber,
                          callbackUrl: String,
                          statusCallbackUrl: Option[String] = None,
                          timeout: Int = 30
                          ) extends TwilioOperation[CallInfo] {

  def request(conf: HttpConfig) = {
    var params = Map(
      "From" -> from.toStandardFormat,
      "To" -> to.toStandardFormat,
      "Url" -> callbackUrl
    )
    statusCallbackUrl.map(params += "StatusCallback" -> _)

    conf.API_BASE / "Calls" << params
  }

  def parser = DialOperation.parse
}

object DialOperation {
  import XmlPredef._

  def parse(res: NodeSeq) = {
    val call = res \ "Call"
    CallInfo(
      call \ "Sid",
      Phonenumber(call \ "From"),
      Phonenumber.parse(call \ "To"),
      call \ "Uri"
    )
  }
}

case class UpdateIncomingPhonenumberConfig(sid: String, config: IncomingPhonenumberConfig) extends TwilioOperation[IncomingPhonenumber] {
  def request(conf: HttpConfig) = {
    var params = Map(
      "ApiVersion" -> Twilio.API_VERSION,
      "VoiceMethod" -> "POST",
      "VoiceFallbackMethod" -> "POST",
      "StatusCallbackMethod" -> "POST",
      "SmsMethod" -> "POST",
      "SmsFallbackMethod" -> "POST"
    )
    val options = List(
      (config.friendlyName -> "FriendlyName"),
      (config.voiceUrl ->"VoiceUrl"),
      (config.statusCallbackUrl -> "StatusCallbackUrl"),
      (config.smsUrl -> "SmsUrl"),
      (config.smsFallbackUrl -> "SmsFallbackUrl")
    )
    params ++= options.flatMap {
      case (Some(opt), setting) => List(setting -> opt)
      case _ => Nil
    }
    conf.API_BASE / "IncomingPhoneNumbers" / sid << params
  }

  def parser = UpdateIncomingPhonenumberConfig.parse
}

object UpdateIncomingPhonenumberConfig {
  import XmlPredef._
  def parse(nodes: NodeSeq) = {
    val number = nodes \ "IncomingPhoneNumber"
    IncomingPhonenumber(
      number \ "Sid",
      IncomingPhonenumberConfig(
        number \ "FriendlyName",
        number \ "VoiceUrl",
        number \ "VoiceFallbackUrl",
        number \ "StatusCallbackUrl",
        number \ "SmsUrl",
        number \ "SmsFallbackUrl"
      )
    )
  }
}

/**
 * Gets the URIs for the participants resources in a conference
 */
case class GetConferenceParticipantURIs(cid: String) extends TwilioOperation[Tuple2[String, Seq[String]]] {
  def request(conf: HttpConfig) = conf.API_BASE / "Conferences" / cid

  def parser = parse

  def parse(res: NodeSeq) = {
    val conf = res \ "Conference"

    (
      (conf \ "Status").text ->
        (conf \ "SubresourceUris" \ "Participants").map{
          _.text
        }
      )
  }
}

case class GetConferenceParticipantInfo(uri: String) extends TwilioOperation[Participant] {
  def request(conf: HttpConfig) = conf.TWILIO_BASE / uri

  def parser = parse
  def parse(res: NodeSeq) = {
    val part = res \ "Participant"
    Participant((part \ "CallSid").text, if ("true" == (part \ "Muted").text) true else false)
  }
}