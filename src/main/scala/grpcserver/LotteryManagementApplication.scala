package grpcserver

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.typesafe.config.ConfigFactory
import grpcserver.actor.{ClusterShardingManagement, HttpServer, LotteryServer, ProjectionManagement}
import grpcserver.middleware.{DefaultClusterShardingManager, DefaultHttpBinder}
import grpcserver.service.{DefaultLotteryServiceApplication, LotteryServiceImpl}
import infrastructure.SlickJdbcSession
import lottery.service.{LotteryService, LotteryServiceHandler}

import java.io.File
import java.time.Clock
import scala.concurrent.Future

final class LotteryManagementApplication(context: ActorContext[_]) {

  val grpcInterface = context.system.settings.config.getString("lottery-service.grpc.interface")
  val grpcPort = context.system.settings.config.getInt("lottery-service.grpc.port")
  val slickConfig = context.system.settings.config.getConfig("slick.dbs")

  val clock: Clock = Clock.systemUTC()

  def behavior(): Behavior[Nothing] = {
    import context.system


    val slickSession = SlickSession.forConfig(slickConfig)
    val httpBinder = new DefaultHttpBinder()
    val clusterShardingManager = new DefaultClusterShardingManager()
    val httpServerRef = context.spawnAnonymous(HttpServer(httpBinder))
    val clusterShardingManagerRef = context.spawnAnonymous(ClusterShardingManagement(clusterShardingManager, clock))
    val projectionManagementRef = context.spawnAnonymous(ProjectionManagement(new SlickJdbcSession(slickSession)))

    val lotteryServerRef = context.spawnAnonymous(
      LotteryServer(
        httpServerRef,
        projectionManagementRef,
        clusterShardingManagerRef
      )
    )

    val lotteryServiceApplication = new DefaultLotteryServiceApplication()


    val lotteryServiceImpl = new LotteryServiceImpl(lotteryServiceApplication)
    val lotteryServer: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        LotteryServiceHandler.partial(lotteryServiceImpl),
        ServerReflection.partial(List(LotteryService))
      )

    lotteryServerRef.tell(
      LotteryServer
        .Launch(
          grpcInterface,
          grpcPort,
          lotteryServer))

    Behaviors.receiveSignal[Nothing] {
      case (_, Terminated(_)) =>
        Behaviors.stopped
    }
  }
}

object LotteryManagementApplication {
  private final val ConfigPath = "CONFIG_PATH"

  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseFileAnySyntax(new File(sys.env(ConfigPath)))
      .withFallback(ConfigFactory.load())
      .resolve()

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      new LotteryManagementApplication(context).behavior()
    }

    ActorSystem[Nothing](rootBehavior, "lottery-service", config)
  }
}
