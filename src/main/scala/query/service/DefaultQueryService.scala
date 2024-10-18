package query.service

import akka.actor.typed.ActorSystem
import akka.stream.alpakka.slick.scaladsl.SlickSession
import query.model.{Lottery, LotteryParticipant, LotteryState, LotteryWinner}
import query.schema.LotterySchema

import java.time.LocalDate
import scala.concurrent.Future

class DefaultQueryService(lotterySchema: LotterySchema, slickSession: SlickSession)
                         (implicit val actorSystem: ActorSystem[_]) extends QueryService {

  import actorSystem.executionContext
  import lotterySchema._
  import slickSession.profile.api._

  override def fetchOpenLotteries(): Future[Seq[Lottery]] = {
    val query = lotterySchema.LotteryQuery
      .filter(_.state === LotteryState.Open)
      .result

    slickSession.db.run(query)
  }

  override def fetchLotteriesWinnersByDate(date: LocalDate): Future[Seq[LotteryWinner]] = {
    val query = lotterySchema.LotteryQuery
      .filter(_.state === LotteryState.Closed)
      .filter(_.createdAt === date)
      .join(lotterySchema.lotteryParticipants)
      .on(_.winner === _.participantId)
      .result
      .map(lotteryWinners =>
        lotteryWinners.map(lotteryWinner =>
          buildLotteryWinner(lotteryWinner._1, lotteryWinner._2)))
          slickSession.db.run(query)
  }

  override def fetchClosedLotteries(): Future[Seq[Lottery]] = {
    val query = lotterySchema.LotteryQuery
      .filter(_.state === LotteryState.Closed)
      .result

    slickSession.db.run(query)
  }

  private[this] def buildLotteryWinner(lottery: Lottery, lotteryParticipant: LotteryParticipant): LotteryWinner =
    LotteryWinner(
      lotteryId = lotteryParticipant.lotteryId,
      lotteryName = lottery.name,
      participantFirstName = lotteryParticipant.participantFirstName,
      participantLastName = lotteryParticipant.participantLastName,
      creationDate = lottery.createdAt,
      winnerId = lottery.winner
    )

}
