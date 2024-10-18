package query.model

import java.time.LocalDate

case class LotteryWinner(lotteryId: String,
                         lottery_name: String,
                         creationDate: LocalDate,
                         winnerId: Option[String])
