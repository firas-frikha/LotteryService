package grpcserver.actor

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.persistence.typed.PersistenceId
import core.LotteryEntity
import grpcserver.middleware.ClusterShardingManager

import java.time.Clock
import java.util.UUID
import scala.util.{Failure, Success}

object ClusterShardingManagement {
  def apply(clusterShardingManager: ClusterShardingManager,
            clock: Clock): Behavior[Input] = {

    Behaviors.setup { context =>
      Behaviors.receiveMessage {
        case RegisterEntity(replyTo) =>
          val entity = Entity(typeKey = LotteryEntity.entityTypeKey) { entityContext =>
            LotteryEntity(id = UUID.fromString(entityContext.entityId),
              persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
              clock = clock
            )
          }
          clusterShardingManager.init(entity) match {
            case Failure(exception) =>
              context.log.error("Failed to initialize '{}' entity!", entity.typeKey.name, exception)
              replyTo.tell(FailedEntityRegistration)
            case Success(_) =>
              context.log.info("'{}' entity was initialized successfully.", entity.typeKey.name)
              replyTo.tell(SuccessfulEntityRegistration)
          }
          Behaviors.same
      }
    }
  }

  sealed trait Input

  sealed trait Output

  case object SuccessfulEntityRegistration extends Output

  case object FailedEntityRegistration extends Output

  case class RegisterEntity(replyTo: ActorRef[Output]) extends Input
}
