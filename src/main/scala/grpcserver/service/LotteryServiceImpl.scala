package grpcserver.service

import akka.actor.typed.{ActorRef, ActorSystem}
import core.LotteryEntity
import lottery.service._

import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class LotteryServiceImpl(lotteryServiceApplication: LotteryServiceApplication)
                        (implicit actorSystem: ActorSystem[_]) extends LotteryService {

  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  override def adminCreateLottery(in: AdminCreateLotteryRequest): Future[AdminCreateLotteryResponse] = {
    val lotteryKey = UUID.randomUUID()

    val createCommand = (replyTo: ActorRef[LotteryEntity.CreateResult]) => LotteryEntity.CreateCommand(in.name)(replyTo.ref)
    lotteryServiceApplication.adminCreateLottery(lotteryKey, createCommand).map {
      case LotteryEntity.SuccessfulCreateResult(id) =>
        AdminCreateLotteryResponse(id.toString)
      case LotteryEntity.UnsupportedCreateResult(message) =>
        throw new UnsupportedOperationException(message)
    }
  }

  override def participateInLottery(in: ParticipateInLotteryRequest): Future[ParticipateInLotteryResponse] = {
    val ballotId = UUID.randomUUID()
    val participateCommand = (replyTo: ActorRef[LotteryEntity.ParticipateResult]) => LotteryEntity.ParticipateCommand(ballotId)(replyTo)
    lotteryServiceApplication.participateInLottery(UUID.fromString(in.lotteryId), participateCommand).map {
      case LotteryEntity.SuccessfulParticipateResult(ballotId) =>
        ParticipateInLotteryResponse(ballotId = ballotId.toString)
      case LotteryEntity.UnsupportedParticipateResult(message) =>
        throw new UnsupportedOperationException(message)
    }
  }
}
