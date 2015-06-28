import akka.http.scaladsl.unmarshalling._

import language._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkasse.EventStreamMarshalling._
import de.heikoseeberger.akkasse._
import akka.http.scaladsl.client.RequestBuilding.Get

import scala.util.{Success, Failure}



object ClientAndServer extends App {
  val unmarshaling: {def fromEntityUnmarshaller: FromEntityUnmarshaller[ServerSentEventSource]} =
    if (args.nonEmpty && args(0) == "with-fix") CustomEventStreamUnmarshalling
    else de.heikoseeberger.akkasse.EventStreamUnmarshalling

  implicit val foo = unmarshaling.fromEntityUnmarshaller

  def toSse(i: Int): ServerSentEvent =
    ServerSentEvent("" + i, "foo")

  def fromSse(sse: ServerSentEvent): Int =
    sse.data.toInt

  def server(): Unit = {
    implicit val system = ActorSystem("server")
    implicit val materializer = ActorFlowMaterializer()
    import system.dispatcher

    val route: Route =
      (path("numbers") & get) {
        complete(Source(() => Iterator.from(1) map toSse))
      }

    Http().bindAndHandle(route, "0.0.0.0", 8080)
  }

  def client(): Unit = {
    implicit val system = ActorSystem("client")
    implicit val materializer = ActorFlowMaterializer()
    import system.dispatcher

    Http() singleRequest Get("http://localhost:8080/numbers") flatMap (Unmarshal(_).to[ServerSentEventSource]) onComplete {
      case Success(source) =>
        source.map(fromSse) runForeach (i => println(s"Got $i")) onComplete {
          case Success(_) => println("Finished")
          case Failure(e) =>
            e.printStackTrace()
            system.shutdown()
        }

      case Failure(e) =>
        e.printStackTrace()
        system.shutdown()
    }
  }

  server()
  client()
}
