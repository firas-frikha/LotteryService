package grpcserver.middleware

import akka.cluster.sharding.typed.scaladsl.Entity

import scala.util.Try

trait ClusterShardingManager {
  def init[M, E](entity: Entity[M, E]): Try[Unit]
}
