package query

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.Outcome
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.FixtureAnyWordSpec
import org.slf4j.LoggerFactory
import query.model.{Lottery, LotteryState, LotteryWinner}
import query.schema.LotterySchema
import query.service.DefaultQueryService

import java.time.LocalDate
import java.util.UUID
import scala.util.Random

class DefaultQueryServiceSpec
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
    val config =
      ConfigFactory.parseString(s"testDatabaseName = test-${UUID.randomUUID().toString}")
        .withFallback(ConfigFactory.parseResourcesAnySyntax("application-test-event.conf"))
        .resolve()

    val actorTestKit = ActorTestKit(
      s"{ActorTestKitBase.testNameFromCallStack()}-${UUID.randomUUID().toString}",
      config
    )

    val slickSession = SlickSession.forConfig("slick.dbs.query", config)
    val lotterySchema = new LotterySchema(slickSession)
    import slickSession.profile.api._

    try {
      val operations = DBIO.seq(
        lotterySchema.LotteryQuery.schema.create
      )
      whenReady(slickSession.db.run(operations), timeout(Span(6, Seconds)), interval(Span(2, Seconds))) { _ =>
        Logger.info("lottery schema table has been created.")
      }
      withFixture(test.toNoArgTest(new TestFixture(actorTestKit, slickSession, lotterySchema)))
    } finally {
      val operations = DBIO.seq(
        lotterySchema.LotteryQuery.schema.dropIfExists
      )
      whenReady(slickSession.db.run(operations), timeout(Span(6, Seconds))) { _ =>
        Logger.info("lottery schema table has been dropped.")
      }
      actorTestKit.shutdownTestKit()
    }
  }

  "DefaultQueryService" when {
    "fetching open lotteries" must {
      val lotteries = Seq(
        Lottery(
          id = UUID.randomUUID().toString,
          name = Random.alphanumeric.take(12).mkString,
          createdAt = LocalDate.now(),
          state = LotteryState.Open,
          winner = None
        ),
        Lottery(
          id = UUID.randomUUID().toString,
          name = Random.alphanumeric.take(12).mkString,
          createdAt = LocalDate.now(),
          state = LotteryState.Open,
          winner = None
        )
      )

      "return valid result" in { testFixture =>
        import testFixture.actorTestKit.system
        import testFixture.slickSession.profile.api._
        import testFixture.{lotterySchema, slickSession}

        val operations = DBIO.seq(
          lotterySchema.LotteryQuery ++= lotteries
        )
        whenReady(slickSession.db.run(operations.transactionally), timeout(Span(5, Seconds))) { _ =>

          val queryService = new DefaultQueryService(lotterySchema, slickSession)

          queryService.fetchOpenLotteries()
            .futureValue(timeout(Span(5, Seconds)))
            .sortBy(_.id)
            .mustBe(lotteries.sortBy(_.id))
        }
      }
    }
    "fetching closed lotteries" must {
      val closedLotteries = Seq(
        Lottery(
          id = UUID.randomUUID().toString,
          name = Random.alphanumeric.take(12).mkString,
          createdAt = LocalDate.now(),
          state = LotteryState.Closed,
        ),
        Lottery(
          id = UUID.randomUUID().toString,
          name = Random.alphanumeric.take(12).mkString,
          createdAt = LocalDate.now(),
          state = LotteryState.Closed,
        )
      )

      "return valid result" in { testFixture =>
        import testFixture.actorTestKit.system
        import testFixture.slickSession.profile.api._
        import testFixture.{lotterySchema, slickSession}

        val operations = DBIO.seq(
          lotterySchema.LotteryQuery ++= closedLotteries
        )
        whenReady(slickSession.db.run(operations.transactionally), timeout(Span(5, Seconds))) { _ =>

          val queryService = new DefaultQueryService(lotterySchema, slickSession)

          queryService.fetchClosedLotteries()
            .futureValue(timeout(Span(5, Seconds)))
            .sortBy(_.id)
            .mustBe(closedLotteries.sortBy(_.id))
        }
      }
    }

    "fetching lotteries winners by date" must {

      val inputDate = LocalDate.now().minusDays(2)

      val finishedLotteries = Seq(
        Lottery(
          id = UUID.randomUUID().toString,
          name = Random.alphanumeric.take(12).mkString,
          createdAt = LocalDate.now().minusDays(2),
          state = LotteryState.Closed,
          winner = Some(UUID.randomUUID().toString)
        ),
        Lottery(
          id = UUID.randomUUID().toString,
          name = Random.alphanumeric.take(12).mkString,
          createdAt = LocalDate.now().minusDays(2),
          state = LotteryState.Closed,
          winner = Some(UUID.randomUUID().toString)
        )
      )

      val lotteryWinners = finishedLotteries.map(lotteryWinner =>
        LotteryWinner(
          lotteryId = lotteryWinner.id,
          lottery_name = lotteryWinner.name,
          creationDate = lotteryWinner.createdAt,
          winnerId = lotteryWinner.winner
        )
      )


      "return valid result" in { testFixture =>

        import testFixture.actorTestKit.system
        import testFixture.slickSession.profile.api._
        import testFixture.{lotterySchema, slickSession}

        val operations = DBIO.seq(
          lotterySchema.LotteryQuery ++= finishedLotteries
        )
        whenReady(slickSession.db.run(operations.transactionally), timeout(Span(5, Seconds))) { _ =>

          val queryService = new DefaultQueryService(lotterySchema, slickSession)

          queryService.fetchLotteriesWinnersByDate(inputDate)
            .futureValue(timeout(Span(5, Seconds)))
            .sortBy(_.lotteryId)
            .mustBe(lotteryWinners.sortBy(_.lotteryId))
        }
      }
    }
  }
}
