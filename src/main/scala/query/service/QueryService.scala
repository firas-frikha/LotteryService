package query.service

import query.model.{Lottery, LotteryWinner}

import java.time.LocalDate
import scala.concurrent.Future

trait QueryService {

  def fetchOpenLotteries(): Future[Seq[Lottery]]

  def fetchLotteriesWinnersByDate(date: LocalDate): Future[Seq[LotteryWinner]]

  def fetchClosedLotteries(): Future[Seq[Lottery]]
}
