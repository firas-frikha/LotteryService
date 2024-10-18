package grpcserver.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import com.google.protobuf.empty.Empty
import core.LotteryEntity
import core.LotteryEntity.{SuccessfulCreateResult, SuccessfulParticipateResult, UnsupportedCreateResult, UnsupportedParticipateResult}
import lottery.service._
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Futures.timeout
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import query.model.{Lottery, LotteryState, LotteryWinner}
import query.service.QueryService

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class LotteryServiceImplSpec
  extends AnyWordSpec
    with Matchers
    with MockFactory
    with BeforeAndAfterAll {


  override def afterAll(): Unit = testKit.shutdownTestKit()

  val testKit = ActorTestKit()

  "LotteryServiceImpl" when {
    "Admin Creating lottery" when {
      "return valid AdminCreateLotteryResponse" in {
        import testKit.system


        val lotteryId: String = UUID.randomUUID().toString
        val adminCreateLotteryRequest =
          AdminCreateLotteryRequest(
            name = Random.alphanumeric.take(12).mkString
          )

        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.adminCreateLottery(_: String, _: ActorRef[LotteryEntity.CreateResult] => LotteryEntity.CreateCommand))
          .expects(*, *)
          .returns(Future(SuccessfulCreateResult(lotteryId)))


        val queryServiceMock = mock[QueryService]

        val lotteryServiceImpl = new LotteryServiceImpl(
          lotteryServiceApplicationMock,
          queryServiceMock)

        lotteryServiceImpl.adminCreateLottery(adminCreateLotteryRequest)
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(AdminCreateLotteryResponse(id = lotteryId.toString))
      }

      "lotteryServiceApplication fails" in {
        import testKit.system

        val exception = new RuntimeException("Unknown exception")

        val adminCreateLotteryRequest =
          AdminCreateLotteryRequest(
            name = Random.alphanumeric.take(12).mkString
          )

        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.adminCreateLottery(_: String, _: ActorRef[LotteryEntity.CreateResult] => LotteryEntity.CreateCommand))
          .expects(*, *)
          .returns(Future(UnsupportedCreateResult(exception.getMessage)))

        val queryServiceMock = mock[QueryService]

        val lotteryServiceImpl = new LotteryServiceImpl(
          lotteryServiceApplicationMock,
          queryServiceMock)

        lotteryServiceImpl.adminCreateLottery(adminCreateLotteryRequest)
          .failed
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(a[UnsupportedOperationException])
      }
    }

    "Participating in lottery" when {

      val lotteryId: String = UUID.randomUUID().toString
      val ballotId: String = UUID.randomUUID().toString
      val participateInLotteryRequest =
        ParticipateInLotteryRequest(
          lotteryId = lotteryId.toString
        )

      "return valid ParticipateToLotteryResponse" in {
        import testKit.system
        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.participateInLottery(_: String, _: ActorRef[LotteryEntity.ParticipateResult] => LotteryEntity.ParticipateCommand))
          .expects(*, *)
          .returns(Future(SuccessfulParticipateResult(ballotId)))

        val queryServiceMock = mock[QueryService]

        val lotteryServiceImpl = new LotteryServiceImpl(
          lotteryServiceApplicationMock,
          queryServiceMock)

        lotteryServiceImpl.participateInLottery(participateInLotteryRequest)
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(ParticipateInLotteryResponse(ballotId = ballotId.toString))
      }
      "lotteryServiceApplication fails" in {
        import testKit.system

        val exception = new RuntimeException("Unknown exception")
        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.participateInLottery(_: String, _: ActorRef[LotteryEntity.ParticipateResult] => LotteryEntity.ParticipateCommand))
          .expects(*, *)
          .returns(Future(UnsupportedParticipateResult(exception.getMessage)))

        val queryServiceMock = mock[QueryService]

        val lotteryServiceImpl = new LotteryServiceImpl(
          lotteryServiceApplicationMock,
          queryServiceMock)

        lotteryServiceImpl.participateInLottery(participateInLotteryRequest)
          .failed
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(a[UnsupportedOperationException])
      }
    }

    "Fetching open lotteries" when {

      "Query service returns valid response" in {
        import testKit.system

        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]

        val queryServiceResponse = Seq(
          Lottery(
            id = UUID.randomUUID().toString,
            name = Random.alphanumeric.take(12).mkString,
            createdAt = LocalDate.now(),
            winner = None,
            state = LotteryState.Open
          ),
          Lottery(
            id = UUID.randomUUID().toString,
            name = Random.alphanumeric.take(12).mkString,
            createdAt = LocalDate.now(),
            winner = None,
            state = LotteryState.Open
          )
        )

        val queryServiceMock = mock[QueryService]
        (queryServiceMock.fetchOpenLotteries _)
          .expects()
          .returns(Future.successful(queryServiceResponse))

        val lotteryServiceImpl = new LotteryServiceImpl(
          lotteryServiceApplicationMock,
          queryServiceMock)

        val expectedResponse =
          FetchOpenLotteriesResponse(
            lotteries =
              queryServiceResponse.map(fetchedLottery =>
                lottery.service.Lottery(
                  lotteryId = fetchedLottery.id,
                  name = fetchedLottery.name,
                  createdAt =
                    Some(Date(
                      year = fetchedLottery.createdAt.getYear,
                      month = fetchedLottery.createdAt.getMonthValue,
                      day = fetchedLottery.createdAt.getDayOfMonth)
                    )
                )
              )
          )

        lotteryServiceImpl.fetchOpenLotteries(Empty())
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(expectedResponse)
      }
    }

    "fetching lotteries results by date" when {
      "Query service returns valid response" in {
        import testKit.system

        val inputDate = LocalDate.now()
        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]

        val fetchLotteriesResultsByDateRequest = FetchLotteriesResultsByDateRequest(
          lotteryDate = Some(
            Date(
              year = inputDate.getYear,
              month = inputDate.getMonthValue,
              day = inputDate.getDayOfMonth,
            )
          )
        )

        val queryServiceResponse = Seq(
          LotteryWinner(
            lotteryId = UUID.randomUUID().toString,
            lottery_name = Random.alphanumeric.take(12).mkString,
            creationDate = inputDate,
            winnerId = Some(UUID.randomUUID().toString)
          ),
          LotteryWinner(
            lotteryId = UUID.randomUUID().toString,
            lottery_name = Random.alphanumeric.take(12).mkString,
            creationDate = inputDate,
            winnerId = Some(UUID.randomUUID().toString)
          )
        )

        val queryServiceMock = mock[QueryService]
        (queryServiceMock.fetchLotteriesWinnersByDate(_: LocalDate))
          .expects(inputDate)
          .returns(Future.successful(queryServiceResponse))

        val lotteryServiceImpl = new LotteryServiceImpl(
          lotteryServiceApplicationMock,
          queryServiceMock)

        val expectedResponse =
          FetchLotteriesResultsByDateResponse(
            lotteriesResult =
              queryServiceResponse.map(lotteryWinner =>
                lottery.service.LotteryResult(
                  lotteryId = lotteryWinner.lotteryId,
                  lotteryName = lotteryWinner.lottery_name,
                  lotteryCreationDate =
                    Some(Date(
                      year = lotteryWinner.creationDate.getYear,
                      month = lotteryWinner.creationDate.getMonthValue,
                      day = lotteryWinner.creationDate.getDayOfMonth)
                    ),
                  lotteryWinner = lotteryWinner.winnerId
                )
              )
          )

        lotteryServiceImpl.fetchLotteriesResultsByDate(fetchLotteriesResultsByDateRequest)
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(expectedResponse)
      }
    }
  }
}
