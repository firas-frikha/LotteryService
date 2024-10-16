package grpcserver.middleware

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

class DefaultHttpBinder()
                       (implicit actorSystem: ActorSystem[_]) extends HttpBinder {
  override def bind(interface: String, port: Int, route: HttpRequest => Future[HttpResponse]): Future[Http.ServerBinding] =
    Http().newServerAt(interface, port).bind(route)
}
