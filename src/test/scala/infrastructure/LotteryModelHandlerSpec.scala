package infrastructure

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ActorTestKitBase}
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.typesafe.config.ConfigFactory
import core.LotteryEntity.{ClosedLotteryEvent, CreatedLotteryEvent, ParticipantAddedEvent}
import core.{LotteryEntity, Participant}
import org.scalamock.scalatest.MockFactory
import org.scalatest.Outcome
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.FixtureAnyWordSpec
import org.slf4j.LoggerFactory
import query.model.{Lottery, LotteryParticipant, LotteryState}
import query.schema.LotterySchema

import java.time.LocalDate
import java.util.UUID
import scala.util.Random

class LotteryModelHandlerSpec
  extends FixtureAnyWordSpec
    with ScalaFutures
    with Matchers
    with MockFactory {

  override type FixtureParam = TestFixture

  final class TestFixture(val actorTestKit: ActorTestKit,
                          val slickSession: SlickSession,
                          val lotterySchema: LotterySchema)

  final val Logger = LoggerFactory.getLogger(getClass)

  override def withFixture(test: OneArgTest): Outcome = {

    val actorTestKit = ActorTestKit(
      s"${ActorTestKitBase.testNameFromCallStack()}-${UUID.randomUUID().toString}",
      ConfigFactory.parseString(s"testDatabaseName = test-${UUID.randomUUID().toString}")
        .withFallback(ConfigFactory.parseResourcesAnySyntax("application-test-event.conf"))
        .withFallback(ConfigFactory.defaultReference())
        .resolve()
    )

    val slickSession = SlickSession.forConfig("slick.dbs.query", actorTestKit.config)
    val lotterySchema = new LotterySchema(slickSession)

    import slickSession.profile.api._
    try {
      val operations = DBIO.seq(
        lotterySchema.LotteryQuery.schema.createIfNotExists,
        lotterySchema.lotteryParticipants.schema.createIfNotExists
      )
      whenReady(slickSession.db.run(operations), timeout(Span(6, Seconds)), interval(Span(2, Seconds))) { _ =>
        Logger.info("lottery schema table has been created.")
      }
      withFixture(test.toNoArgTest(new TestFixture(actorTestKit, slickSession, lotterySchema)))
    } finally {
      val operations = DBIO.seq(
        lotterySchema.LotteryQuery.schema.dropIfExists,
        lotterySchema.lotteryParticipants.schema.dropIfExists
      )
      whenReady(slickSession.db.run(operations), timeout(Span(6, Seconds)), interval(Span(2, Seconds))) { _ =>
        Logger.info("lottery schema table has been dropped.")
      }
      actorTestKit.shutdownTestKit()
    }
  }

  "LotteryModelHandler" when {
    "processing CreatedLotteryEvent event" must {
      "successfully persist data" when {
        "lottery do not exist" in { testFixture =>

          import testFixture.slickSession
          import testFixture.slickSession.profile.api._

          val lotteryId = UUID.randomUUID().toString

          val createdLotteryEvent = CreatedLotteryEvent(
            id = lotteryId,
            lotteryName = Random.alphanumeric.take(12).mkString,
            createdAt = LocalDate.now()
          )

          val savedLotteryRecord = Lottery(
            id = lotteryId,
            name = createdLotteryEvent.lotteryName,
            createdAt = createdLotteryEvent.createdAt,
            winner = None,
            state = LotteryState.Open
          )

          val handler = new LotteryModelHandler()

          val slickJdbcSession = new SlickJdbcSession(slickSession)

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              createdLotteryEvent.id,
              Random.nextLong(),
              createdLotteryEvent,
              System.currentTimeMillis()
            )
          )

          slickJdbcSession.commit()
          slickJdbcSession.close()

          slickSession.db.run(testFixture.lotterySchema.LotteryQuery.result)
            .futureValue(timeout(Span(5, Seconds)))
            .mustBe(Seq(savedLotteryRecord))

        }
        "do persist data" when {

          "lottery exists" in { testFixture =>
            import testFixture.slickSession
            import testFixture.slickSession.profile.api._

            val lotteryId = UUID.randomUUID().toString

            val createdLotteryEvent = CreatedLotteryEvent(
              id = lotteryId,
              lotteryName = Random.alphanumeric.take(12).mkString,
              createdAt = LocalDate.now()
            )

            val createdLotteryEventTwo = CreatedLotteryEvent(
              id = lotteryId,
              lotteryName = Random.alphanumeric.take(12).mkString,
              createdAt = LocalDate.now()
            )


            val savedLotteryRecord = Lottery(
              id = lotteryId,
              name = createdLotteryEvent.lotteryName,
              createdAt = createdLotteryEvent.createdAt,
              winner = None,
              state = LotteryState.Open
            )

            val handler = new LotteryModelHandler()

            val slickJdbcSession = new SlickJdbcSession(slickSession)

            handler.process(
              slickJdbcSession,
              EventEnvelope.create[LotteryEntity.Event](
                Offset.noOffset,
                createdLotteryEvent.id,
                Random.nextLong(),
                createdLotteryEvent,
                System.currentTimeMillis()
              )
            )

            handler.process(
              slickJdbcSession,
              EventEnvelope.create[LotteryEntity.Event](
                Offset.noOffset,
                createdLotteryEventTwo.id,
                Random.nextLong(),
                createdLotteryEventTwo,
                System.currentTimeMillis()
              )
            )

            slickJdbcSession.commit()
            slickJdbcSession.close()

            slickSession.db.run(testFixture.lotterySchema.LotteryQuery.result)
              .futureValue(timeout(Span(5, Seconds)))
              .mustBe(Seq(savedLotteryRecord))

          }
        }
      }
    }
    "processing BallotAddedEvent event" must {
      "successfully persist data" when {
        "lottery exists" in { testFixture =>

          import testFixture.slickSession
          import testFixture.slickSession.profile.api._

          val lotteryId = UUID.randomUUID().toString
          val participantId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = participantId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString)

          val createdLotteryEvent = CreatedLotteryEvent(
            id = lotteryId,
            lotteryName = Random.alphanumeric.take(12).mkString,
            createdAt = LocalDate.now()
          )

          val participantAddedEvent = ParticipantAddedEvent(
            id = lotteryId,
            lotteryParticipant = Set(participant)
          )

          val savedParticipants = LotteryParticipant(
            participantId = participantId,
            participantFirstName = participant.participantFirstName,
            participantLastName = participant.participantLastName,
            lotteryId = lotteryId
          )

          val handler = new LotteryModelHandler()

          val slickJdbcSession = new SlickJdbcSession(slickSession)

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              createdLotteryEvent.id,
              Random.nextLong(),
              createdLotteryEvent,
              System.currentTimeMillis()
            )
          )


          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              participantAddedEvent.id,
              Random.nextLong(),
              participantAddedEvent,
              System.currentTimeMillis()
            )
          )

          slickJdbcSession.commit()
          slickJdbcSession.close()

          slickSession.db.run(testFixture.lotterySchema.lotteryParticipants.result)
            .futureValue(timeout(Span(5, Seconds)))
            .mustBe(Seq(savedParticipants))

        }
      }

      "do not persist data" when {
        "lottery do not exist" in { testFixture =>

          import testFixture.slickSession
          import testFixture.slickSession.profile.api._


          val lotteryId = UUID.randomUUID().toString
          val participantId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = participantId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString

          )

          val participantAddedEvent = ParticipantAddedEvent(
            id = lotteryId,
            lotteryParticipant = Set(participant)
          )

          val handler = new LotteryModelHandler()

          val slickJdbcSession = new SlickJdbcSession(slickSession)

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              participantAddedEvent.id,
              Random.nextLong(),
              participantAddedEvent,
              System.currentTimeMillis()
            )
          )

          slickJdbcSession.commit()
          slickJdbcSession.close()

          slickSession.db.run(testFixture.lotterySchema.lotteryParticipants.result)
            .futureValue(timeout(Span(5, Seconds)))
            .mustBe(Seq.empty)

        }
      }
    }

    "processing ClosedLotteryEvent event" must {
      "successfully persist data" when {
        "lottery exists" in { testFixture =>

          import testFixture.slickSession
          import testFixture.slickSession.profile.api._

          val lotteryId = UUID.randomUUID().toString
          val participantId = UUID.randomUUID().toString

          val createdLotteryEvent = CreatedLotteryEvent(
            id = lotteryId,
            lotteryName = Random.alphanumeric.take(12).mkString,
            createdAt = LocalDate.now()
          )

          val participant = Participant(
            participantId = participantId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString)

          val participantAddedEvent = ParticipantAddedEvent(
            id = lotteryId,
            lotteryParticipant = Set(participant)
          )

          val LotteryClosedEvent = ClosedLotteryEvent(
            id = lotteryId,
            lotteryName = createdLotteryEvent.lotteryName,
            createdAt = createdLotteryEvent.createdAt,
            participants = participantAddedEvent.lotteryParticipant,
            winner = Option(participant)
          )

          val closedLotteryRecord = Lottery(
            id = createdLotteryEvent.id,
            name = createdLotteryEvent.lotteryName,
            createdAt = createdLotteryEvent.createdAt,
            winner = LotteryClosedEvent.winner.map(_.participantId),
            state = LotteryState.Closed
          )

          val handler = new LotteryModelHandler()

          val slickJdbcSession = new SlickJdbcSession(slickSession)

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              createdLotteryEvent.id,
              Random.nextLong(),
              createdLotteryEvent,
              System.currentTimeMillis()
            )
          )


          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              participantAddedEvent.id,
              Random.nextLong(),
              participantAddedEvent,
              System.currentTimeMillis()
            )
          )

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              LotteryClosedEvent.id,
              Random.nextLong(),
              LotteryClosedEvent,
              System.currentTimeMillis()
            )
          )

          slickJdbcSession.commit()
          slickJdbcSession.close()

          slickSession.db.run(testFixture.lotterySchema.LotteryQuery.result)
            .futureValue(timeout(Span(5, Seconds)))
            .mustBe(Seq(closedLotteryRecord))

        }

        "lottery exists but no participant" in { testFixture =>

          import testFixture.slickSession
          import testFixture.slickSession.profile.api._

          val lotteryId = UUID.randomUUID().toString

          val createdLotteryEvent = CreatedLotteryEvent(
            id = lotteryId,
            lotteryName = Random.alphanumeric.take(12).mkString,
            createdAt = LocalDate.now()
          )


          val LotteryClosedEvent = ClosedLotteryEvent(
            id = lotteryId,
            lotteryName = createdLotteryEvent.lotteryName,
            createdAt = createdLotteryEvent.createdAt,
            participants = Set.empty,
            winner = None
          )

          val closedLotteryRecord = Lottery(
            id = createdLotteryEvent.id,
            name = createdLotteryEvent.lotteryName,
            createdAt = createdLotteryEvent.createdAt,
            winner = None,
            state = LotteryState.Closed
          )

          val handler = new LotteryModelHandler()

          val slickJdbcSession = new SlickJdbcSession(testFixture.slickSession)

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              createdLotteryEvent.id,
              Random.nextLong(),
              createdLotteryEvent,
              System.currentTimeMillis()
            )
          )

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              LotteryClosedEvent.id,
              Random.nextLong(),
              LotteryClosedEvent,
              System.currentTimeMillis()
            )
          )

          slickJdbcSession.commit()
          slickJdbcSession.close()

          slickSession.db.run(testFixture.lotterySchema.LotteryQuery.result)
            .futureValue(timeout(Span(5, Seconds)))
            .mustBe(Seq(closedLotteryRecord))

        }
      }
      "do not persist data" when {
        "lottery dot not exist" in { testFixture =>

          import testFixture.slickSession
          import testFixture.slickSession.profile.api._


          val lotteryId = UUID.randomUUID().toString
          val participantId = UUID.randomUUID().toString

          val participant =
            Participant(
              participantId = participantId,
              participantFirstName = Random.alphanumeric.take(12).mkString,
              participantLastName = Random.alphanumeric.take(12).mkString
            )

          val LotteryClosedEvent = ClosedLotteryEvent(
            id = lotteryId,
            lotteryName = UUID.randomUUID().toString,
            createdAt = LocalDate.now(),
            participants = Set(participant),
            winner = Option(participant)
          )


          val handler = new LotteryModelHandler()

          val slickJdbcSession = new SlickJdbcSession(slickSession)

          handler.process(
            slickJdbcSession,
            EventEnvelope.create[LotteryEntity.Event](
              Offset.noOffset,
              LotteryClosedEvent.id,
              Random.nextLong(),
              LotteryClosedEvent,
              System.currentTimeMillis()
            )
          )

          slickJdbcSession.commit()
          slickJdbcSession.close()

          slickSession.db.run(testFixture.lotterySchema.LotteryQuery.result)
            .futureValue(timeout(Span(5, Seconds)))
            .mustBe(Seq.empty)
        }
      }
    }
  }
}
