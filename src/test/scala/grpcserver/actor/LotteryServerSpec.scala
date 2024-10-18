package grpcserver.actor

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import query.service.QueryService

import java.time.Clock
import scala.concurrent.Future

class LotteryServerSpec
  extends AnyWordSpec
    with BeforeAndAfterAll
    with Matchers
    with MockFactory {

  override def afterAll(): Unit = testKit.shutdownTestKit()

  val testKit = ActorTestKit()

  "LotteryServer" when {
    val clock = Clock.systemUTC()

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

          val projectionManagementProbe = testKit.createTestProbe[ProjectionManagement.Input]()

          val projectionManagementBehavior = Behaviors.receiveMessagePartial[ProjectionManagement.Input] {
            case ProjectionManagement.StartProjection(replyTo) =>
              replyTo.tell(ProjectionManagement.SuccessfulProjectionInitialization)

              Behaviors.same
          }


          val lotteryServerProbe = testKit.createTestProbe[LotteryServer.Input]()

          val lotteryServerBehavior = LotteryServer(
            httpServerRef = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, httpServerBehavior)),
            clusterShardingManagerRef = testKit.spawn(Behaviors.monitor(clusterShardingManagerProbe.ref, clusterShardingManagerBehavior)),
            projectionManagement = testKit.spawn(Behaviors.monitor(projectionManagementProbe.ref, projectionManagementBehavior)),
            clock = clock,
            queryService = mock[QueryService])


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
        "clusterShardingManager replies with FailedEntityRegistration" in {

          val httpHost = "0.0.0.0"
          val httpPort = 8080
          val httpRoute = mock[HttpRequest => Future[HttpResponse]]

          val httpServerProbe = testKit.createTestProbe[HttpServer.Input]()

          val clusterShardingManagerProbe = testKit.createTestProbe[ClusterShardingManagement.Input]()

          val clusterShardingManagerBehavior = Behaviors.receiveMessagePartial[ClusterShardingManagement.Input] {
            case ClusterShardingManagement.RegisterEntity(replyTo) =>
              replyTo.tell(ClusterShardingManagement.FailedEntityRegistration)

              Behaviors.same
          }

          val lotteryServerProbe = testKit.createTestProbe[LotteryServer.Input]()

          val projectionManagementProbe = testKit.createTestProbe[ProjectionManagement.Input]()

          val lotteryServerBehavior = LotteryServer(
            httpServerRef = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, Behaviors.empty[HttpServer.Input])),
            clusterShardingManagerRef = testKit.spawn(Behaviors.monitor(clusterShardingManagerProbe.ref, clusterShardingManagerBehavior)),
            projectionManagement = testKit.spawn(Behaviors.monitor(projectionManagementProbe.ref, Behaviors.empty[ProjectionManagement.Input])),
            clock = clock,
            queryService = mock[QueryService]
          )


          val lotteryServerActor = testKit.spawn(Behaviors.monitor(lotteryServerProbe.ref, lotteryServerBehavior))

          val serverLaunchCommand = LotteryServer.Launch(httpHost, httpPort, httpRoute)

          lotteryServerActor.tell(serverLaunchCommand)
          clusterShardingManagerProbe.expectMessageType[ClusterShardingManagement.RegisterEntity]
          clusterShardingManagerProbe.expectNoMessage()

          httpServerProbe.expectNoMessage()


          lotteryServerProbe.expectMessage(serverLaunchCommand)
        }

        "ProjectionManagement replies with FailedEntityRegistration" in {

          val httpHost = "0.0.0.0"
          val httpPort = 8080
          val httpRoute = mock[HttpRequest => Future[HttpResponse]]

          val httpServerProbe = testKit.createTestProbe[HttpServer.Input]()

          val clusterShardingManagerProbe = testKit.createTestProbe[ClusterShardingManagement.Input]()

          val clusterShardingManagerBehavior = Behaviors.receiveMessagePartial[ClusterShardingManagement.Input] {
            case ClusterShardingManagement.RegisterEntity(replyTo) =>
              replyTo.tell(ClusterShardingManagement.SuccessfulEntityRegistration)

              Behaviors.same
          }

          val projectionManagementProbe = testKit.createTestProbe[ProjectionManagement.Input]()
          val projectionManagementBehavior = Behaviors.receiveMessagePartial[ProjectionManagement.Input] {
            case ProjectionManagement.StartProjection(replyTo) =>
              replyTo.tell(ProjectionManagement.FailedProjectionInitialization)

              Behaviors.same
          }

          val lotteryServerProbe = testKit.createTestProbe[LotteryServer.Input]()
          val lotteryServerBehavior = LotteryServer(
            httpServerRef = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, Behaviors.empty[HttpServer.Input])),
            clusterShardingManagerRef = testKit.spawn(Behaviors.monitor(clusterShardingManagerProbe.ref, clusterShardingManagerBehavior)),
            projectionManagement = testKit.spawn(Behaviors.monitor(projectionManagementProbe.ref, projectionManagementBehavior)),
            clock = clock,
            queryService = mock[QueryService]
          )


          val lotteryServerActor = testKit.spawn(Behaviors.monitor(lotteryServerProbe.ref, lotteryServerBehavior))

          val serverLaunchCommand = LotteryServer.Launch(httpHost, httpPort, httpRoute)

          lotteryServerActor.tell(serverLaunchCommand)

          clusterShardingManagerProbe.expectMessageType[ClusterShardingManagement.RegisterEntity]
          clusterShardingManagerProbe.expectNoMessage()

          projectionManagementProbe.expectMessageType[ProjectionManagement.StartProjection]


          httpServerProbe.expectNoMessage()

          lotteryServerProbe.expectMessage(serverLaunchCommand)
        }

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

          val projectionManagementProbe = testKit.createTestProbe[ProjectionManagement.Input]()

          val projectionManagementBehavior = Behaviors.receiveMessagePartial[ProjectionManagement.Input] {
            case ProjectionManagement.StartProjection(replyTo) =>
              replyTo.tell(ProjectionManagement.SuccessfulProjectionInitialization)

              Behaviors.same
          }

          val lotteryServerBehavior = LotteryServer(
            httpServerRef = testKit.spawn(Behaviors.monitor(httpServerProbe.ref, httpServerBehavior)),
            clusterShardingManagerRef = testKit.spawn(Behaviors.monitor(clusterShardingManagerProbe.ref, clusterShardingManagerBehavior)),
            projectionManagement = testKit.spawn(Behaviors.monitor(projectionManagementProbe.ref, projectionManagementBehavior)),
            clock = clock,
            queryService = mock[QueryService]
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
