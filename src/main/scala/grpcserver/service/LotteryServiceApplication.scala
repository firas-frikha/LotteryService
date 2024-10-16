package grpcserver.service

import akka.actor.typed.ActorRef
import core.LotteryEntity

import scala.concurrent.Future

trait LotteryServiceApplication {
  def adminCreateLottery(ballotId: LotteryEntity.Id, command: ActorRef[LotteryEntity.CreateResult] => LotteryEntity.CreateCommand): Future[LotteryEntity.CreateResult]

  def participateInLottery(ballotId: LotteryEntity.Id, command: ActorRef[LotteryEntity.ParticipateResult] => LotteryEntity.ParticipateCommand): Future[LotteryEntity.ParticipateResult]
}
