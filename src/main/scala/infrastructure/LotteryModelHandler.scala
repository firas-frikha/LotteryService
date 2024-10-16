package infrastructure

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import core.LotteryEntity
import core.LotteryEntity.{BallotAddedEvent, ClosedLotteryEvent, CreatedLotteryEvent}
import infrastructure.LotteryModelHandler._
import org.slf4j.LoggerFactory

import java.sql.{Connection, Timestamp}
import java.time.LocalDateTime

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
      if (!selectLotteryById(createdLotteryEvent.id.toString, connection)) {
        insertLottery(createdLotteryEvent, connection)
      }
      else {
        Log.warn("Lottery with id: {} already exist", createdLotteryEvent.id)
      }
    }

  private[this] def process(session: SlickJdbcSession, closedLotteryEvent: ClosedLotteryEvent): Unit =
    session.withConnection { connection =>
      val preparedStatement = connection.prepareStatement(UpdateLotteryState)
      preparedStatement.setString(1, "CLOSED")
      preparedStatement.setString(2, closedLotteryEvent.id.toString)

      val updateLotteryState = preparedStatement.executeUpdate()

      if (updateLotteryState > 0)
        Log.info("Lottery with id: {} state changed to CLOSED", closedLotteryEvent.id)
      else
        Log.warn("Update Command cannot be executed, lottery with id: {} may not exist", closedLotteryEvent.id)
    }

  private[this] def process(session: SlickJdbcSession, ballotAddedEvent: BallotAddedEvent): Unit =
    session.withConnection { connection =>
      val participantArrays = createArray(connection, ballotAddedEvent.newBallotsList.map(_.toString))
      val preparedStatement = connection.prepareStatement(UpdateBallotForLotterySql)
      preparedStatement.setArray(1, participantArrays)
      preparedStatement.setString(2, ballotAddedEvent.id.toString)
      val updateResult = preparedStatement.executeUpdate()
      if (updateResult > 0)
        Log.info("Participant list updated Correctly for lottery {}", ballotAddedEvent.id)
      else
        Log.warn("Update Command cannot be executed, lottery with id: {} may not exist", ballotAddedEvent.id)
    }


  private[this] def createArray(connection: Connection, array: Set[String]): java.sql.Array =
    connection.createArrayOf("VARCHAR", array.toArray)

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

  private[this] def insertLottery(createdLotteryEvent: CreatedLotteryEvent, connection: Connection): Int = {
    val preparedStatement = connection.prepareStatement(LotteryInsertSql)
    preparedStatement.setString(1, createdLotteryEvent.id.toString)
    preparedStatement.setString(2, createdLotteryEvent.lotteryName)
    preparedStatement.setTimestamp(3, convert(createdLotteryEvent.createdAt))
    preparedStatement.setString(4, "OPEN")
    preparedStatement.executeUpdate()
  }

  private[this] def convert(localDateTime: LocalDateTime): Timestamp =
    Timestamp.valueOf(localDateTime)
}

object LotteryModelHandler {
  private final val Log = LoggerFactory.getLogger(classOf[LotteryModelHandler])

  private final val LotteryExistSelectSql = "SELECT Count(lottery_id) from lotteries where lottery_id = ?"
  private final val LotteryInsertSql = "INSERT INTO lotteries(lottery_id, lottery_name, created_at, lottery_state) VALUES(?, ?, ?, ?)"
  private final val UpdateBallotForLotterySql = "UPDATE lotteries SET participants = ? where lottery_id = ?"
  private final val UpdateLotteryState = "UPDATE lotteries SET lottery_state = ? where lottery_id = ?"
}
