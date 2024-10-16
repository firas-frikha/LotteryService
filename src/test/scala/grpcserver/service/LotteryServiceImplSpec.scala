package grpcserver.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorRef
import core.LotteryEntity
import core.LotteryEntity.{SuccessfulCreateResult, SuccessfulParticipateResult, UnsupportedCreateResult, UnsupportedParticipateResult}
import lottery.service.{AdminCreateLotteryRequest, AdminCreateLotteryResponse, ParticipateInLotteryRequest, ParticipateInLotteryResponse}
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Futures.timeout
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

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


        val lotteryId: UUID = UUID.randomUUID()
        val adminCreateLotteryRequest =
          AdminCreateLotteryRequest(
            name = Random.alphanumeric.take(12).mkString
          )

        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.adminCreateLottery(_: UUID, _: ActorRef[LotteryEntity.CreateResult] => LotteryEntity.CreateCommand))
          .expects(*, *)
          .returns(Future(SuccessfulCreateResult(lotteryId)))


        val lotteryServiceImpl = new LotteryServiceImpl(lotteryServiceApplicationMock)

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
        (lotteryServiceApplicationMock.adminCreateLottery(_: UUID, _: ActorRef[LotteryEntity.CreateResult] => LotteryEntity.CreateCommand))
          .expects(*, *)
          .returns(Future(UnsupportedCreateResult(exception.getMessage)))

        val lotteryServiceImpl = new LotteryServiceImpl(lotteryServiceApplicationMock)

        lotteryServiceImpl.adminCreateLottery(adminCreateLotteryRequest)
          .failed
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(a[UnsupportedOperationException])
      }
    }

    "Participating in lottery" when {

      val lotteryId: UUID = UUID.randomUUID()
      val ballotId: UUID = UUID.randomUUID()
      val participateInLotteryRequest =
        ParticipateInLotteryRequest(
          lotteryId = lotteryId.toString
        )

      "return valid ParticipateToLotteryResponse" in {
        import testKit.system
        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.participateInLottery(_: UUID, _: ActorRef[LotteryEntity.ParticipateResult] => LotteryEntity.ParticipateCommand))
          .expects(*, *)
          .returns(Future(SuccessfulParticipateResult(ballotId)))

        val lotteryServiceImpl = new LotteryServiceImpl(lotteryServiceApplicationMock)

        lotteryServiceImpl.participateInLottery(participateInLotteryRequest)
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(ParticipateInLotteryResponse(ballotId = ballotId.toString))
      }
      "lotteryServiceApplication fails" in {
        import testKit.system

        val exception = new RuntimeException("Unknown exception")
        val lotteryServiceApplicationMock = mock[LotteryServiceApplication]
        (lotteryServiceApplicationMock.participateInLottery(_: UUID, _: ActorRef[LotteryEntity.ParticipateResult] => LotteryEntity.ParticipateCommand))
          .expects(*, *)
          .returns(Future(UnsupportedParticipateResult(exception.getMessage)))

        val lotteryServiceImpl = new LotteryServiceImpl(lotteryServiceApplicationMock)

        lotteryServiceImpl.participateInLottery(participateInLotteryRequest)
          .failed
          .futureValue(timeout(Span(5, Seconds)))
          .mustBe(a[UnsupportedOperationException])
      }
    }
  }
}
