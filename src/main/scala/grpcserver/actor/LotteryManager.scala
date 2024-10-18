package grpcserver.actor

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import core.LotteryEntity
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import query.service.QueryService

import java.time.temporal.ChronoUnit
import java.time.{Clock, LocalDateTime}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}

object LotteryManager {
  def apply(queryService: QueryService,
            clock: Clock): Behavior[Input] = Behaviors.setup { context =>

    val closeResultMessageAdapter = context.messageAdapter(WrappedCloseResultOutput)
    import context.executionContext
    val sharding = ClusterSharding(context.system)

    context.system.scheduler.scheduleAtFixedRate(
      initialDelay = calculateInitialDelay(clock),
      interval = 24.hours
    ) { () => context.self ! CloseOpenLottery }

    Behaviors.receiveMessage {
      case CloseOpenLottery =>
        queryService.fetchOpenLotteries().map {
          openLottery =>
            openLottery.foreach { lottery =>
              val lotteryRef = sharding.entityRefFor(LotteryEntity.entityTypeKey, lottery.id)
              lotteryRef ! LotteryEntity.CloseLotteryCommand()(closeResultMessageAdapter)
            }
        }
        Behaviors.same
      case WrappedCloseResultOutput(LotteryEntity.SuccessfulCloseResult(_)) =>
        context.log.info("Succeeded to Close lottery")
        Behaviors.same
      case WrappedCloseResultOutput(LotteryEntity.UnsupportedCloseResult(message)) =>
        context.log.warn("Failed to close lottery: {}", message)

        Behaviors.same
    }
  }

  sealed trait Input

  case object CloseOpenLottery extends Input

  case class WrappedCloseResultOutput(output: LotteryEntity.CloseResult) extends Input

  private def calculateInitialDelay(clock: Clock): FiniteDuration = {
    val now = LocalDateTime.now(clock)
    val midnight = now.toLocalDate.atStartOfDay.plusDays(1)
    val millisUntilMidnight = ChronoUnit.MILLIS.between(now, midnight)
    FiniteDuration(millisUntilMidnight, MILLISECONDS)
  }
}