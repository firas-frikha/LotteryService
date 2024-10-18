package query.service

import akka.actor.typed.ActorSystem
import akka.stream.alpakka.slick.scaladsl.SlickSession
import query.model.{Lottery, LotteryState, LotteryWinner}
import query.schema.LotterySchema

import java.time.LocalDate
import scala.concurrent.Future

class DefaultQueryService(lotterySchema: LotterySchema, slickSession: SlickSession)
                         (implicit val actorSystem: ActorSystem[_]) extends QueryService {

  import actorSystem.executionContext
  import slickSession.profile.api._
  import lotterySchema._
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
      .result
      .map(buildLotteryWinners)
    slickSession.db.run(query)
  }

  override def fetchClosedLotteries(): Future[Seq[Lottery]] = {
    val query = lotterySchema.LotteryQuery
      .filter(_.state === LotteryState.Closed)
      .result

    slickSession.db.run(query)
  }

  private[this] def buildLotteryWinners(lotteries: Seq[Lottery]): Seq[LotteryWinner] =
    lotteries.map(lottery =>
      LotteryWinner(
        lotteryId = lottery.id,
        lottery_name = lottery.name,
        creationDate = lottery.createdAt,
        winnerId = lottery.winner
      )
    )

}
