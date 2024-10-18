package query.model

import java.time.LocalDate

case class LotteryWinner(lotteryId: String,
                         lotteryName: String,
                         participantFirstName: String,
                         participantLastName: String,
                         creationDate: LocalDate,
                         winnerId: Option[String])
