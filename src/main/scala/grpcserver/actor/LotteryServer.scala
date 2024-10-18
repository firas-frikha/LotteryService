package grpcserver.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.typed.{ClusterSingleton, SingletonActor}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import query.service.QueryService

import java.time.Clock
import scala.concurrent.Future

object LotteryServer {
  def apply(httpServerRef: ActorRef[HttpServer.Input],
            projectionManagement: ActorRef[ProjectionManagement.Input],
            clusterShardingManagerRef: ActorRef[ClusterShardingManagement.Input],
            clock: Clock,
            queryService: QueryService): Behavior[Input] = Behaviors.setup { context =>


    def handle(host: String,
               port: Int,
               route: HttpRequest => Future[HttpResponse]): Behavior[Mediating] = Behaviors.setup { mediatorContext =>
      val httpServerMessageAdapter = mediatorContext.messageAdapter(WrappedHttpServerOutput)
      val clusterShardingManagerMessageAdapter = mediatorContext.messageAdapter(WrappedClusterShardingManagerOutput)
      val projectionManagementMessageAdapter = mediatorContext.messageAdapter(WrappedProjectionManagementOutput)

      Behaviors.receiveMessage {
        case Start =>
          clusterShardingManagerRef.tell(ClusterShardingManagement.RegisterEntity(clusterShardingManagerMessageAdapter))

          Behaviors.same

        case WrappedClusterShardingManagerOutput(ClusterShardingManagement.FailedEntityRegistration) =>
          Behaviors.stopped

        case WrappedClusterShardingManagerOutput(ClusterShardingManagement.SuccessfulEntityRegistration) =>
          projectionManagement.tell(ProjectionManagement.StartProjection(projectionManagementMessageAdapter))
          Behaviors.same

        case WrappedProjectionManagementOutput(ProjectionManagement.SuccessfulProjectionInitialization) =>
          httpServerRef.tell(HttpServer.Bind(host, port, route, httpServerMessageAdapter))
          Behaviors.same

        case WrappedProjectionManagementOutput(ProjectionManagement.FailedProjectionInitialization) =>
          Behaviors.same

        case WrappedHttpServerOutput(HttpServer.FailedBinding) =>
          Behaviors.stopped

        case WrappedHttpServerOutput(HttpServer.SuccessfulBinding) =>
          val singletonManager = ClusterSingleton(context.system)
          singletonManager.init(
            SingletonActor(LotteryManager(queryService = queryService, clock = clock), "Lottery-Manager")
          )
          Behaviors.ignore
      }
    }

    Behaviors.receiveMessage {
      case Launch(host, port, route) =>
        context.spawnAnonymous(handle(host, port, route)).tell(Start)

        Behaviors.same
    }
  }

  sealed trait Input

  sealed trait Mediating

  case object Start extends Mediating

  final case class Launch(host: String,
                          port: Int,
                          route: HttpRequest => Future[HttpResponse]) extends Input

  final case class WrappedHttpServerOutput(httpServerOutput: HttpServer.Output) extends Mediating

  final case class WrappedClusterShardingManagerOutput(clusterShardingManagerOutput: ClusterShardingManagement.Output) extends Mediating

  final case class WrappedProjectionManagementOutput(projectionManagementOutput: ProjectionManagement.Output) extends Mediating

}
