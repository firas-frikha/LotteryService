package grpcserver.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HttpConnectionTerminated
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import grpcserver.middleware.HttpBinder
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class HttpServerSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with MockFactory {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val testKit = ActorTestKit()

  "HttpServer" when {
    "Receiving Bind message" must {
      "Succeed to bind server" when {
        "httpBinder Succeed" in {
          val host = Random.alphanumeric.take(12).mkString
          val port = Random.nextInt(10)
          val route = mock[HttpRequest => Future[HttpResponse]]

          val binding = Http.ServerBinding(new InetSocketAddress(host, port))(() => Future.successful(()), _ => Future.successful(HttpConnectionTerminated))

          val httpBinderMock = mock[HttpBinder]
          (httpBinderMock.bind(_: String, _: Int, _: HttpRequest => Future[HttpResponse]))
            .expects(*, *, *)
            .returns(Future(binding))


          val httpServerBehavior = HttpServer(
            httpBinder = httpBinderMock)

          val outputProbe = testKit.createTestProbe[HttpServer.Output]()

          val httpServerProbe = testKit.createTestProbe[HttpServer.Input]()
          val httpServerActor = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, httpServerBehavior))

          val bindMessage = HttpServer.Bind(host = host, port = port, route = route, replyTo = outputProbe.ref)

          httpServerActor.tell(bindMessage)

          httpServerProbe.expectMessage(bindMessage)

          outputProbe.expectMessage(HttpServer.SuccessfulBinding)
        }
      }

      "Fails to bind server" when {
        "httpBinder Fail" in {
          val host = Random.alphanumeric.take(12).mkString
          val port = Random.nextInt(10)
          val route = mock[HttpRequest => Future[HttpResponse]]

          val binding = Http.ServerBinding(new InetSocketAddress(host, port))(() => Future.successful(()), _ => Future.successful(HttpConnectionTerminated))

          val exception = new RuntimeException("Unknown exception")

          val httpBinderMock = mock[HttpBinder]
          (httpBinderMock.bind(_: String, _: Int, _: HttpRequest => Future[HttpResponse]))
            .expects(*, *, *)
            .returns(Future.failed(exception))

          val httpServerBehavior = HttpServer(
            httpBinder = httpBinderMock)

          val outputProbe = testKit.createTestProbe[HttpServer.Output]()

          val httpServerProbe = testKit.createTestProbe[HttpServer.Input]()
          val httpServerActor = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, httpServerBehavior))

          val bindMessage = HttpServer.Bind(host = host, port = port, route = route, replyTo = outputProbe.ref)

          httpServerActor.tell(bindMessage)

          httpServerProbe.expectMessage(bindMessage)

          outputProbe.expectMessage(HttpServer.FailedBinding)
        }
      }
    }
  }
}
