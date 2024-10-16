package grpcserver.middleware

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}

import scala.util.Try


class DefaultClusterShardingManager()(implicit val actorSystem: ActorSystem[_]) extends ClusterShardingManager {
  override def init[M, E](entity: Entity[M, E]): Try[Unit] =
    Try {
      ClusterSharding(actorSystem).init(entity)
    }
}
