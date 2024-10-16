package core

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import core.LotteryEntity._
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.temporal.ChronoUnit
import java.time.{Clock, LocalDateTime, ZoneOffset, ZonedDateTime}
import java.util.UUID
import scala.util.Random


object LotteryEntitySpec {
  val config = EventSourcedBehaviorTestKit.config
    .withFallback(ConfigFactory.parseResourcesAnySyntax("application-test.conf"))
}

class LotteryEntitySpec
  extends ScalaTestWithActorTestKit(LotteryEntitySpec.config)
    with AnyWordSpecLike {

  "LotteryEntity actor" when {

    val entityId = UUID.randomUUID()
    val lotteryName = Random.alphanumeric.take(12).mkString
    val clock = Clock.fixed(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(10).toInstant, ZoneOffset.UTC)

    "is in EmptyState" when {
      "receiving CreateCommand" must {
        "reply with SuccessfulCreateResult" in {

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)
        }
      }

      "receiving Participate command" must {
        "reply with UnsupportedParticipateResult" in {
          val ballotId = UUID.randomUUID()

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val failedResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            ParticipateCommand(ballotId)
          )

          failedResult.hasNoEvents shouldBe true
          failedResult.stateOfType[LotteryEntity.EmptyState].id shouldBe entityId
          failedResult.reply should ===(LotteryEntity.UnsupportedParticipateResult(s"Cannot Process ${classOf[LotteryEntity.ParticipateCommand].getSimpleName} command, because lottery with id: $entityId do not exist!"))
        }
      }

      "receiving CloseLottery command" must {
        "reply with UnsupportedCloseResult" in {

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val failedResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            CloseLotteryCommand()
          )

          failedResult.hasNoEvents shouldBe true
          failedResult.stateOfType[LotteryEntity.EmptyState].id shouldBe entityId
          failedResult.reply should ===(LotteryEntity.UnsupportedCloseResult(s"Cannot Process ${classOf[LotteryEntity.CloseLotteryCommand].getSimpleName} command, because lottery with id: $entityId do not exist!"))
        }
      }

      "receiving FetchLotteryWinner command" must {
        "reply with UnsupportedFetchLotteryWinnerResult" in {

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val failedResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.FetchLotteryWinnerResult](
            FetchLotteryWinner()
          )

          failedResult.hasNoEvents shouldBe true
          failedResult.stateOfType[LotteryEntity.EmptyState].id shouldBe entityId
          failedResult.reply should ===(LotteryEntity.UnsupportedFetchLotteryWinnerResult(s"Cannot Process ${classOf[LotteryEntity.FetchLotteryWinner].getSimpleName} command, because lottery with id: $entityId do not exist!"))
        }
      }
    }

    "is in ActiveState" when {
      "receiving CreateCommand" must {
        "reply with UnsupportedCreateResult" in {

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val unsupportedCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          unsupportedCreateResult.hasNoEvents shouldBe true
          unsupportedCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          unsupportedCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          unsupportedCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          unsupportedCreateResult.reply shouldBe LotteryEntity.UnsupportedCreateResult(s"Cannot Process ${classOf[LotteryEntity.CreateCommand].getSimpleName} command, because lottery with '$entityId' is already created!")

        }
      }

      "receiving Participate command" must {
        "reply with SuccessfulParticipateResult" in {
          val ballotId = UUID.randomUUID()

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val ballotAddedEvent = BallotAddedEvent(
            id = entityId,
            newBallotsList = Set(ballotId)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val successfulParticipateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(
              ballotId = ballotId
            )
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(ballotId)
        }
      }

      "receiving CloseLottery command" must {
        "reply with SuccessfulCloseResult" in {

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val successfulCloseResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            LotteryEntity.CloseLotteryCommand()
          )

          successfulCloseResult.event shouldBe a[ClosedLotteryEvent]
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt
        }
      }

      "receiving FetchLotteryWinner command" must {
        "reply with UnsupportedFetchLotteryWinnerResult" in {
          val ballotId = UUID.randomUUID()

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val ballotAddedEvent = BallotAddedEvent(
            id = entityId,
            newBallotsList = Set(ballotId)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val successfulParticipateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(
              ballotId = ballotId
            )
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(ballotId)

          val unsupportedFetchLotteryWinnerResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.FetchLotteryWinnerResult](
            LotteryEntity.FetchLotteryWinner()
          )

          unsupportedFetchLotteryWinnerResult.hasNoEvents shouldBe true
          unsupportedFetchLotteryWinnerResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          unsupportedFetchLotteryWinnerResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          unsupportedFetchLotteryWinnerResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
        }
      }
    }


    "is in ClosedState" when {
      "receiving CreateCommand" must {
        "reply with UnsupportedCreateResult" in {
          val ballotId = UUID.randomUUID()

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val successfulParticipateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(
              ballotId = ballotId
            )
          )

          val ballotAddedEvent = BallotAddedEvent(
            id = entityId,
            newBallotsList = Set(ballotId)
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(ballotId)


          val successfulCloseResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            LotteryEntity.CloseLotteryCommand()
          )

          successfulCloseResult.event shouldBe a[ClosedLotteryEvent]
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt


          val unsupportedCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          unsupportedCreateResult.hasNoEvents shouldBe true
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt
          unsupportedCreateResult.reply shouldBe LotteryEntity.UnsupportedCreateResult(s"Cannot process ${classOf[LotteryEntity.CreateCommand].getSimpleName} command, because lottery with id: '$entityId' is closed!")

        }
      }

      "receiving Participate command" must {
        "reply with UnsupportedParticipateResult" in {
          val ballotId = UUID.randomUUID()

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val successfulParticipateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(
              ballotId = ballotId
            )
          )

          val ballotAddedEvent = BallotAddedEvent(
            id = entityId,
            newBallotsList = Set(ballotId)
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(ballotId)


          val successfulCloseResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            LotteryEntity.CloseLotteryCommand()
          )

          successfulCloseResult.event shouldBe a[ClosedLotteryEvent]
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt


          val unsupportedCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(ballotId)
          )

          unsupportedCreateResult.hasNoEvents shouldBe true
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt
          unsupportedCreateResult.reply shouldBe LotteryEntity.UnsupportedParticipateResult(s"Cannot process ${classOf[LotteryEntity.ParticipateCommand].getSimpleName} command, because lottery with id: '$entityId' is closed!")

        }
      }

      "receiving CloseLottery command" must {
        "reply with UnsupportedSuccessfulCloseResult" in {
          val ballotId = UUID.randomUUID()

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
          )

          val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
            LotteryEntity.CreateCommand(
              lotteryName = lotteryName
            )
          )

          successfulCreateResult.event shouldBe createdEvent
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

          val successfulParticipateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(
              ballotId = ballotId
            )
          )

          val ballotAddedEvent = BallotAddedEvent(
            id = entityId,
            newBallotsList = Set(ballotId)
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(ballotId)


          val successfulCloseResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            LotteryEntity.CloseLotteryCommand()
          )

          successfulCloseResult.event shouldBe a[ClosedLotteryEvent]
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt


          val unsupportedCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            LotteryEntity.CloseLotteryCommand()
          )

          unsupportedCreateResult.hasNoEvents shouldBe true
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          unsupportedCreateResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt
          unsupportedCreateResult.reply shouldBe LotteryEntity.UnsupportedCloseResult(s"Cannot process ${classOf[LotteryEntity.CloseLotteryCommand].getSimpleName} command, because lottery with id: '$entityId' is already closed!")

        }
      }
    }

    "receiving FetchLotteryWinner command" must {
      "reply with SuccessfulFetchLotteryWinnerResult" in {
        val ballotId = UUID.randomUUID()

        val createdEvent = CreatedLotteryEvent(
          id = entityId,
          lotteryName = lotteryName,
          createdAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
        )

        val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
          system = system,
          behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId.toString), clock)
        )

        val successfulCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CreateResult](
          LotteryEntity.CreateCommand(
            lotteryName = lotteryName
          )
        )

        successfulCreateResult.event shouldBe createdEvent
        successfulCreateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
        successfulCreateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
        successfulCreateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
        successfulCreateResult.reply shouldBe LotteryEntity.SuccessfulCreateResult(createdEvent.id)

        val successfulParticipateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
          LotteryEntity.ParticipateCommand(
            ballotId = ballotId
          )
        )

        val ballotAddedEvent = BallotAddedEvent(
          id = entityId,
          newBallotsList = Set(ballotId)
        )

        successfulParticipateResult.event shouldBe ballotAddedEvent
        successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
        successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
        successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
        successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(ballotId)


        val successfulCloseResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
          LotteryEntity.CloseLotteryCommand()
        )

        successfulCloseResult.event shouldBe a[ClosedLotteryEvent]
        successfulCloseResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
        successfulCloseResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
        successfulCloseResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt


        val fetchLotteryWinnerResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.FetchLotteryWinnerResult](
          LotteryEntity.FetchLotteryWinner()
        )

        fetchLotteryWinnerResult.hasNoEvents shouldBe true
        fetchLotteryWinnerResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
        fetchLotteryWinnerResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
        fetchLotteryWinnerResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt
        fetchLotteryWinnerResult.reply shouldBe a[LotteryEntity.SuccessfulFetchLotteryWinnerResult]
      }
    }
  }
}
