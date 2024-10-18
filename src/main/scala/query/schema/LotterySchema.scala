package query.schema

import akka.stream.alpakka.slick.scaladsl.SlickSession
import query.model.{Lottery, LotteryParticipant, LotteryState}
import slick.ast.BaseTypedType
import slick.lifted.ProvenShape

import java.time.LocalDate

final class LotterySchema(session: SlickSession) {

  import session.profile.api._

  final val LotteryQuery = TableQuery[Lotteries]
  final val lotteryParticipants = TableQuery[LotteryParticipants]

  implicit val lotteryStateMapper: BaseTypedType[LotteryState.State] = MappedColumnType.base(_.toString, LotteryState.withName)

  private[LotterySchema] final class Lotteries(tag: Tag) extends Table[Lottery](tag, "lotteries") {

    override def * = (id, lotteryName, createdAt, winner, state) <> (Lottery.tupled, Lottery.unapply)

    def id: Rep[String] = column[String]("lottery_id", O.PrimaryKey)

    def lotteryName: Rep[String] = column[String]("lottery_name")

    def createdAt: Rep[LocalDate] = column[LocalDate]("created_at")

    def state: Rep[LotteryState.State] = column[LotteryState.State]("lottery_state")

    def winner: Rep[Option[String]] = column[Option[String]]("winner")
  }

  private[LotterySchema] final class LotteryParticipants(tag: Tag) extends Table[LotteryParticipant](tag, "lottery_participants") {

    def participantId: Rep[String] = column[String]("participant_id", O.PrimaryKey)

    def lotteryId: Rep[String] = column[String]("lottery_id")

    def participantFirstName: Rep[String] = column[String]("participant_first_name")

    def participantLastName: Rep[String] = column[String]("participant_last_name")

    override def * : ProvenShape[LotteryParticipant] = (participantId, participantFirstName, participantLastName, lotteryId) <> (LotteryParticipant.tupled, LotteryParticipant.unapply)
  }
}
