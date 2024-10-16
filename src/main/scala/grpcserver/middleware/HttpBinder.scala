package grpcserver.middleware

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

trait HttpBinder {
  def bind(host: String, port: Int, route: HttpRequest => Future[HttpResponse]): Future[Http.ServerBinding]
}
