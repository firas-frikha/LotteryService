package query.model

import java.time.LocalDate

final case class Lottery(id: String,
                   name: String,
                   createdAt: LocalDate,
                   winner: Option[String] = None,
                   state: LotteryState.State)
