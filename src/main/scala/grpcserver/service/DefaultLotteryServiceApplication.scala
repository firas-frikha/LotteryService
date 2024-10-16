package grpcserver.service

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import core.LotteryEntity
import core.LotteryEntity.Id
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime

import scala.concurrent.Future

final class DefaultLotteryServiceApplication()
                                            (implicit actorSystem: ActorSystem[_]) extends LotteryServiceApplication {
  override def adminCreateLottery(ballotId: Id, command: ActorRef[LotteryEntity.CreateResult] => LotteryEntity.CreateCommand): Future[LotteryEntity.CreateResult] = {

    implicit val askTimeout = Timeout(5.seconds)
    ClusterSharding(actorSystem).entityRefFor(LotteryEntity.entityTypeKey, ballotId.toString)
      .ask(command)
  }

  override def participateInLottery(ballotId: Id, command: ActorRef[LotteryEntity.ParticipateResult] => LotteryEntity.ParticipateCommand): Future[LotteryEntity.ParticipateResult] = {
    implicit val askTimeout = Timeout(5.seconds)
    ClusterSharding(actorSystem).entityRefFor(LotteryEntity.entityTypeKey, ballotId.toString)
      .ask(command)
  }
}
