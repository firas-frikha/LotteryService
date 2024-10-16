package grpcserver.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.SourceProvider
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.stream.alpakka.slick.javadsl.SlickSession
import core.LotteryEntity
import infrastructure.{LotteryModelHandler, SlickJdbcSession}

import scala.util.{Failure, Success, Try}

object ProjectionManagement {

  def apply(slickJdbcSession: SlickJdbcSession): Behavior[Input] = Behaviors.setup { context =>

    import context.system

    Behaviors.receiveMessage {
      case startProjection: StartProjection =>
        val eventSourcedProvider: SourceProvider[Offset, EventEnvelope[LotteryEntity.Event]] = {
          EventSourcedProvider.eventsByTag[LotteryEntity.Event](
            system = context.system,
            readJournalPluginId = JdbcReadJournal.Identifier,
            tag = LotteryEntity.Single
          )
        }
        Try {

          val projection = JdbcProjection.atLeastOnce(
            projectionId = ProjectionId("lottery-view", LotteryEntity.Single),
            eventSourcedProvider,
            () => new SlickJdbcSession(SlickSession.forConfig(context.system.settings.config.getConfig("slick.dbs.projection"))),
            handler = () => new LotteryModelHandler()
          )

          ShardedDaemonProcess(context.system).init(
            name = "LotteryProjection",
            numberOfInstances = 4,
            (_) => ProjectionBehavior(projection)
          )
        } match {
          case Failure(exception) =>
            startProjection.replyTo.tell(FailedProjectionInitialization(exception.getMessage))
            context.log.error(exception.getMessage)
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

  case class FailedProjectionInitialization(message: String) extends Output

  case class StartProjection(replyTo: ActorRef[Output]) extends Input
}
