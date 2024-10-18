package grpcserver.service

import akka.actor.typed.{ActorRef, ActorSystem}
import com.google.protobuf.empty.Empty
import core.LotteryEntity
import lottery.service._
import query.service.QueryService

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.{ExecutionContextExecutor, Future}

class LotteryServiceImpl(lotteryServiceApplication: LotteryServiceApplication,
                         queryService: QueryService)
                        (implicit actorSystem: ActorSystem[_]) extends LotteryService {

  implicit val executionContext: ExecutionContextExecutor = actorSystem.executionContext

  override def adminCreateLottery(in: AdminCreateLotteryRequest): Future[AdminCreateLotteryResponse] = {
    val lotteryKey = UUID.randomUUID()

    val createCommand = (replyTo: ActorRef[LotteryEntity.CreateResult]) => LotteryEntity.CreateCommand(in.name)(replyTo.ref)
    lotteryServiceApplication.adminCreateLottery(lotteryKey.toString, createCommand).map {
      case LotteryEntity.SuccessfulCreateResult(id) =>
        AdminCreateLotteryResponse(id)
      case LotteryEntity.UnsupportedCreateResult(message) =>
        throw new UnsupportedOperationException(message)
    }
  }

  override def participateInLottery(in: ParticipateInLotteryRequest): Future[ParticipateInLotteryResponse] = {
    val ballotId = UUID.randomUUID().toString
    val participateCommand = (replyTo: ActorRef[LotteryEntity.ParticipateResult]) => LotteryEntity.ParticipateCommand(ballotId)(replyTo)
    lotteryServiceApplication.participateInLottery(in.lotteryId, participateCommand).map {
      case LotteryEntity.SuccessfulParticipateResult(ballotId) =>
        ParticipateInLotteryResponse(ballotId = ballotId)
      case LotteryEntity.UnsupportedParticipateResult(message) =>
        throw new UnsupportedOperationException(message)
    }
  }

  override def fetchOpenLotteries(in: Empty): Future[FetchOpenLotteriesResponse] =
    queryService.fetchOpenLotteries()
      .map(lot => FetchOpenLotteriesResponse(
        lotteries = lot.map(lottery =>
          Lottery(
            lotteryId = lottery.id,
            name = lottery.name,
            createdAt = Some(
              Date(
                year = lottery.createdAt.getYear,
                month = lottery.createdAt.getMonthValue,
                day = lottery.createdAt.getDayOfMonth)))))
      )

  override def fetchLotteriesResultsByDate(in: FetchLotteriesResultsByDateRequest): Future[FetchLotteriesResultsByDateResponse] =
    queryService.
      fetchLotteriesWinnersByDate(date = LocalDate.of(in.lotteryDate.get.year, in.lotteryDate.get.month, in.lotteryDate.get.day))
      .map(lotteryWinners =>
        FetchLotteriesResultsByDateResponse(
          lotteriesResult =
            lotteryWinners.map(lotteryWinner =>
              LotteryResult(
                lotteryId = lotteryWinner.lotteryId,
                lotteryName = lotteryWinner.lottery_name,
                lotteryCreationDate = Some(toDate(lotteryWinner.creationDate)),
                lotteryWinner = lotteryWinner.winnerId)
            )
        )
      )

  private def toDate(localDate: LocalDate): Date =
    Date(
      year = localDate.getYear,
      month = localDate.getMonthValue,
      day = localDate.getDayOfMonth)

}
