package infrastructure

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import core.LotteryEntity
import core.LotteryEntity.{BallotAddedEvent, ClosedLotteryEvent, CreatedLotteryEvent}
import infrastructure.LotteryModelHandler._
import org.slf4j.LoggerFactory
import query.model.LotteryState

import java.sql.{Connection, Date}
import java.time.LocalDate

class LotteryModelHandler() extends JdbcHandler[EventEnvelope[LotteryEntity.Event], SlickJdbcSession] {
  override def process(session: SlickJdbcSession, envelope: EventEnvelope[LotteryEntity.Event]): Unit =
    envelope.event match {
      case createdLotteryEvent: LotteryEntity.CreatedLotteryEvent =>
        process(session, createdLotteryEvent)
      case ballotAddedEvent: LotteryEntity.BallotAddedEvent =>
        process(session, ballotAddedEvent)
      case closedLotteryEvent: LotteryEntity.ClosedLotteryEvent =>
        process(session, closedLotteryEvent)
    }


  private[this] def process(session: SlickJdbcSession, createdLotteryEvent: CreatedLotteryEvent): Unit =
    session.withConnection { connection =>
      if (!selectLotteryById(createdLotteryEvent.id, connection)) {
        insertLottery(createdLotteryEvent, connection)
      }
      else {
        Log.warn("Lottery with id: {} already exist", createdLotteryEvent.id)
      }
    }

  private[this] def process(session: SlickJdbcSession, closedLotteryEvent: ClosedLotteryEvent): Unit =
    session.withConnection { connection =>
      val preparedStatement = connection.prepareStatement(UpdateLotteryState)
      preparedStatement.setString(1, LotteryState.Closed.toString)
      preparedStatement.setString(2, closedLotteryEvent.winner.orNull)

      preparedStatement.setString(3, closedLotteryEvent.id)

      val updateLotteryState = preparedStatement.executeUpdate()

      if (updateLotteryState > 0)
        Log.info("Lottery with id: {} state changed to CLOSED", closedLotteryEvent.id)
      else
        Log.warn("Update Command cannot be executed, lottery with id: {} may not exist", closedLotteryEvent.id)
    }

  private[this] def process(session: SlickJdbcSession, ballotAddedEvent: BallotAddedEvent): Unit =
    session.withConnection { connection =>

      if (selectLotteryById(ballotAddedEvent.id, connection)) {
        ballotAddedEvent.newBallotsList.foreach { ballotId =>
          if (!selectParticipantById(ballotId, connection)) {
            val preparedStatement = connection.prepareStatement(AddParticipantToLotterySql)
            preparedStatement.setString(1, ballotId)
            preparedStatement.setString(2, ballotAddedEvent.id)
            preparedStatement.executeUpdate()
          } else {
            Log.warn("Participant already registered to lottery")
          }
        }
      } else {
        Log.warn("Lottery with id: {} do not exist, skipping registering participant", ballotAddedEvent.id)
      }
    }

  private[this] def selectLotteryById(lotteryId: String, connection: Connection): Boolean = {
    val preparedStatement = connection.prepareStatement(LotteryExistSelectSql)
    preparedStatement.setString(1, lotteryId)
    val result = preparedStatement.executeQuery()
    if (result.next()) {
      result.getInt(1) > 0
    }
    else
      false
  }

  private[this] def selectParticipantById(participantID: String, connection: Connection): Boolean = {
    val preparedStatement = connection.prepareStatement(ParticipantExistSelectSql)
    preparedStatement.setString(1, participantID)
    val result = preparedStatement.executeQuery()
    if (result.next()) {
      result.getInt(1) > 0
    }
    else
      false
  }


  private[this] def insertLottery(createdLotteryEvent: CreatedLotteryEvent, connection: Connection): Int = {
    val preparedStatement = connection.prepareStatement(LotteryInsertSql)
    preparedStatement.setString(1, createdLotteryEvent.id)
    preparedStatement.setString(2, createdLotteryEvent.lotteryName)
    preparedStatement.setDate(3, convert(createdLotteryEvent.createdAt))
    preparedStatement.setString(4, LotteryState.Open.toString)
    preparedStatement.executeUpdate()
  }

  private[this] def convert(localDateTime: LocalDate): Date =
    Date.valueOf(localDateTime)
}

object LotteryModelHandler {
  private final val Log = LoggerFactory.getLogger(classOf[LotteryModelHandler])

  private final val LotteryExistSelectSql = "SELECT Count(lottery_id) from lotteries WHERE lottery_id = ?"
  private final val LotteryInsertSql = "INSERT INTO lotteries(lottery_id, lottery_name, created_at, lottery_state) VALUES(?, ?, ?, ?)"
  private final val UpdateLotteryState = "UPDATE lotteries SET lottery_state = ?, winner = ? WHERE lottery_id = ?"

  private final val AddParticipantToLotterySql = "INSERT INTO lottery_participants(participant_id, lottery_id) VALUES(?,?)"
  private final val ParticipantExistSelectSql = "SELECT Count(participant_id) from lottery_participants WHERE participant_id = ?"
}
