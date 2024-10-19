package grpcserver.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.stream.alpakka.slick.javadsl.SlickSession
import core.LotteryEntity
import infrastructure.{LotteryModelHandler, SlickJdbcSession}

import scala.util.{Failure, Success, Try}

object ProjectionManagement {
  def apply(): Behavior[Input] = Behaviors.setup { context =>
    import context.system

    Behaviors.receiveMessage {
      case startProjection: StartProjection =>
        Try {

          ShardedDaemonProcess(context.system).init(
            name = "LotteryProjection",
            numberOfInstances = LotteryEntity.Tags.size,
            behaviorFactory = (instance: Int) => ProjectionBehavior(
              JdbcProjection.atLeastOnce(
                projectionId = ProjectionId("lottery-view", LotteryEntity.Tags(instance)),
                sourceProvider = EventSourcedProvider.eventsByTag[LotteryEntity.Event](
                  system = context.system,
                  readJournalPluginId = JdbcReadJournal.Identifier,
                  tag = LotteryEntity.Tags(instance)
                ),
                () => new SlickJdbcSession(SlickSession.forConfig(context.system.settings.config.getConfig("slick.dbs"))),
                handler = () => new LotteryModelHandler()
              )
            )
          )
        } match {
          case Failure(exception) =>
            context.log.error(exception.getMessage)
            startProjection.replyTo.tell(FailedProjectionInitialization)

            Behaviors.same
          case Success(_) =>
            startProjection.replyTo.tell(SuccessfulProjectionInitialization)
            Behaviors.same
        }
    }
  }


  sealed trait Input

  sealed trait Output

  case object SuccessfulProjectionInitialization extends Output

  case object FailedProjectionInitialization extends Output

  case class StartProjection(replyTo: ActorRef[Output]) extends Input
}
