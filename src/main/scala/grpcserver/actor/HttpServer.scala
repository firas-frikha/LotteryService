package grpcserver.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import grpcserver.middleware.HttpBinder

import scala.concurrent.Future
import scala.util.{Failure, Success}

object HttpServer {

  def apply(httpBinder: HttpBinder): Behavior[Input] = Behaviors.setup { context =>

    Behaviors.receiveMessage {
      case Bind(host, port, route, replyTo) =>

        context.pipeToSelf(httpBinder.bind(host, port, route)) {
          case Failure(exception) =>
            LogBindingFailure(exception, replyTo)
          case Success(binding) =>
            LogBindingSuccess(binding, replyTo)
        }
        Behaviors.same

      case LogBindingSuccess(binding, replyTo) =>
        context.log.info("Lottery server online at http://{}:{}/", binding.localAddress.getHostString, binding.localAddress.getPort)

        replyTo.tell(SuccessfulBinding)

        Behaviors.same

      case LogBindingFailure(exception, replyTo) =>
        context.log.info("Failed to bind HTTP endpoint!", exception)
        replyTo.tell(FailedBinding)

        Behaviors.same
    }
  }


  sealed trait Input


  sealed trait Output

  case class Bind(host: String,
                  port: Int,
                  route: HttpRequest => Future[HttpResponse],
                  replyTo: ActorRef[Output]) extends Input

  private[this] final case class LogBindingSuccess(binding: Http.ServerBinding, replyTo: ActorRef[Output]) extends Input

  private[this] final case class LogBindingFailure(exception: Throwable, replyTo: ActorRef[Output]) extends Input

  case object SuccessfulBinding extends Output

  case object FailedBinding extends Output
}