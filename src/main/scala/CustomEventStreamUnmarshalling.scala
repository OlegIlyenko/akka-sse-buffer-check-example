import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
import akka.http.scaladsl.util.FastFuture
import akka.stream.stage.{Context, StatefulStage, PushStage}
import akka.util.ByteString
import de.heikoseeberger.akkasse._
import scala.concurrent.ExecutionContext

object CustomEventStreamUnmarshalling extends CustomEventStreamUnmarshalling

trait CustomEventStreamUnmarshalling {

  private val maxSize = bufferMaxSize

  /**
   * The maximum buffer size for the event Stream parser; 8KiB by default.
   */
  def bufferMaxSize: Int = 8192

  implicit final def fromEntityUnmarshaller: FromEntityUnmarshaller[ServerSentEventSource] = {
    val unmarshaller: FromEntityUnmarshaller[ServerSentEventSource] = Unmarshaller { ec => entity =>
      FastFuture.successful(entity
          .dataBytes
          .transform(() => new CustomLineParser(maxSize))
          .transform(() => new CustomServerSentEventParser(maxSize)))
    }
    unmarshaller.forContentTypes(MediaTypes.`text/event-stream`)
  }
}

private object CustomLineParser {

  final val CR = '\r'.toByte

  final val LF = '\n'.toByte
}

private final class CustomLineParser(maxSize: Int) extends StatefulStage[ByteString, String] {
  import CustomLineParser._

  private var buffer = ByteString.empty

  override def initial = new State {
    override def onPush(bytes: ByteString, ctx: Context[String]) = {
      buffer ++= bytes

      val parsedLines = lines().iterator

      if (buffer.size > maxSize)
        ctx.fail(new IllegalStateException(s"maxSize of $maxSize exceeded!"))
      else
        emit(parsedLines, ctx)
    }

    private def lines(): Vector[String] = {
      val (lines, nrOfConsumedBytes, _) = (buffer :+ 0)
          .zipWithIndex
          .sliding(2)
          .collect {
        case Seq((CR, n), (LF, _)) => (n, 2)
        case Seq((CR, n), _)       => (n, 1)
        case Seq((LF, n), _)       => (n, 1)
      }
          .foldLeft((Vector.empty[String], 0, false)) {
        case ((slices, from, false), (until, k)) => (slices :+ buffer.slice(from, until).utf8String, until + k, k == 2)
        case ((slices, _, _), (until, _))        => (slices, until + 1, false)
      }
      buffer = buffer.drop(nrOfConsumedBytes)
      lines
    }
  }
}

private object CustomServerSentEventParser {

  private final val LF = "\n"

  private final val Data = "data"

  private final val Event = "event"

  private val linePattern = """([^:]+): ?(.*)""".r

  private def parseServerSentEvent(lines: Seq[String]) = {
    val valuesByField = lines
        .collect { case linePattern(field @ (Data | Event), value) => field -> value }
        .groupBy(_._1)
    val data = valuesByField.getOrElse(Data, Vector.empty).map(_._2).mkString(LF)
    val event = valuesByField.getOrElse(Event, Vector.empty).lastOption.map(_._2)
    ServerSentEvent(data, event)
  }
}

private final class CustomServerSentEventParser(maxSize: Int) extends PushStage[String, ServerSentEvent] {
  import CustomServerSentEventParser._

  private var lines = Vector.empty[String]

  override def onPush(line: String, ctx: Context[ServerSentEvent]) =
    if (line.nonEmpty) {
      lines :+= line
      if (lines.map(_.length).sum > maxSize)
        ctx.fail(new IllegalStateException(s"maxSize of $maxSize exceeded!"))
      else
        ctx.pull()
    } else {
      val event = parseServerSentEvent(lines)
      lines = Vector.empty
      ctx.push(event)
    }
}