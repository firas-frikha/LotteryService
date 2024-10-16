package grpcserver.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future

class LotteryServerSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with MockFactory {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val testKit = ActorTestKit()

  "LotteryServer" when {
    "Receiving Launch message" must {
      "Succeed to Launch Server" when {
        "Http server replies with SuccessfulBinding" in {

          val httpHost = "0.0.0.0"
          val httpPort = 8080
          val httpRoute = mock[HttpRequest => Future[HttpResponse]]

          val httpServerProbe = testKit.createTestProbe[HttpServer.Input]()

          val httpServerBehavior = Behaviors.receiveMessagePartial[HttpServer.Input] {
            case HttpServer.Bind(host, port, route, replyTo) if host == httpHost & port == httpPort & route == httpRoute =>
              replyTo.tell(HttpServer.SuccessfulBinding)

              Behaviors.same
          }

          val clusterShardingManagerProbe = testKit.createTestProbe[ClusterShardingManagement.Input]()

          val clusterShardingManagerBehavior = Behaviors.receiveMessagePartial[ClusterShardingManagement.Input] {
            case ClusterShardingManagement.RegisterEntity(replyTo) =>
              replyTo.tell(ClusterShardingManagement.SuccessfulEntityRegistration)

              Behaviors.same
          }

          val lotteryServerProbe = testKit.createTestProbe[LotteryServer.Input]()

          val lotteryServerBehavior = LotteryServer(
            httpServerRef = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, httpServerBehavior)),
            clusterShardingManagerRef = testKit.spawn(Behaviors.monitor(clusterShardingManagerProbe.ref, clusterShardingManagerBehavior))
          )


          val lotteryServerActor = testKit.spawn(Behaviors.monitor(lotteryServerProbe.ref, lotteryServerBehavior))

          val serverLaunchCommand = LotteryServer.Launch(httpHost, httpPort, httpRoute)

          lotteryServerActor.tell(serverLaunchCommand)

          httpServerProbe.expectMessageType[HttpServer.Bind]
          httpServerProbe.expectNoMessage()

          clusterShardingManagerProbe.expectMessageType[ClusterShardingManagement.RegisterEntity]
          clusterShardingManagerProbe.expectNoMessage()

          lotteryServerProbe.expectMessage(serverLaunchCommand)
          lotteryServerProbe.expectNoMessage()
        }
      }

      "Fail to Launch Server" when {
        "Http server replies with FailedBinding" in {

          val httpHost = "0.0.0.0"
          val httpPort = 8080
          val httpRoute = mock[HttpRequest => Future[HttpResponse]]

          val httpServerProbe = testKit.createTestProbe[HttpServer.Input]()

          val httpServerBehavior = Behaviors.receiveMessagePartial[HttpServer.Input] {
            case HttpServer.Bind(host, port, route, replyTo) if host == httpHost & port == httpPort & route == httpRoute =>
              replyTo.tell(HttpServer.FailedBinding)

              Behaviors.same
          }

          val clusterShardingManagerProbe = testKit.createTestProbe[ClusterShardingManagement.Input]()

          val clusterShardingManagerBehavior = Behaviors.receiveMessagePartial[ClusterShardingManagement.Input] {
            case ClusterShardingManagement.RegisterEntity(replyTo) =>
              replyTo.tell(ClusterShardingManagement.SuccessfulEntityRegistration)

              Behaviors.same
          }

          val lotteryServerProbe = testKit.createTestProbe[LotteryServer.Input]()

          val lotteryServerBehavior = LotteryServer(
            httpServerRef = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, httpServerBehavior)),
            clusterShardingManagerRef = testKit.spawn(Behaviors.monitor(clusterShardingManagerProbe.ref, clusterShardingManagerBehavior))
          )


          val lotteryServerActor = testKit.spawn(Behaviors.monitor(lotteryServerProbe.ref, lotteryServerBehavior))

          val serverLaunchCommand = LotteryServer.Launch(httpHost, httpPort, httpRoute)

          lotteryServerActor.tell(serverLaunchCommand)

          httpServerProbe.expectMessageType[HttpServer.Bind]
          httpServerProbe.expectNoMessage()


          clusterShardingManagerProbe.expectMessageType[ClusterShardingManagement.RegisterEntity]
          clusterShardingManagerProbe.expectNoMessage()

          lotteryServerProbe.expectMessage(serverLaunchCommand)
          lotteryServerProbe.expectNoMessage()
        }
      }
    }
  }
}
