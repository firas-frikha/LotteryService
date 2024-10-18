package core

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import core.LotteryEntity._
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.{Clock, LocalDate, ZoneOffset, ZonedDateTime}
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

    val entityId = UUID.randomUUID().toString
    val lotteryName = Random.alphanumeric.take(12).mkString
    val clock = Clock.fixed(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(10).toInstant, ZoneOffset.UTC)

    "is in EmptyState" when {
      "receiving CreateCommand" must {
        "reply with SuccessfulCreateResult" in {

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
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
          val ballotId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = ballotId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString
          )
          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId), clock)
          )

          val failedResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            ParticipateCommand(participant)
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
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId), clock)
          )

          val failedResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            CloseLotteryCommand()
          )

          failedResult.hasNoEvents shouldBe true
          failedResult.stateOfType[LotteryEntity.EmptyState].id shouldBe entityId
          failedResult.reply should ===(LotteryEntity.UnsupportedCloseResult(s"Cannot Process ${classOf[LotteryEntity.CloseLotteryCommand].getSimpleName} command, because lottery with id: $entityId do not exist!"))
        }
      }
    }

    "is in ActiveState" when {
      "receiving CreateCommand" must {
        "reply with UnsupportedCreateResult" in {

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
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
          val ballotId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = ballotId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString
          )

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
          )

          val ballotAddedEvent = ParticipantAddedEvent(
            id = entityId,
            lotteryParticipant = Set(participant)
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
              participant = participant
            )
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(participant)
        }
      }

      "receiving CloseLottery command" must {
        "reply with SuccessfulCloseResult" in {

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
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
    }


    "is in ClosedState" when {
      "receiving CreateCommand" must {
        "reply with UnsupportedCreateResult" in {
          val participantId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = participantId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString
          )

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId), clock)
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
              participant = participant
            )
          )

          val ballotAddedEvent = ParticipantAddedEvent(
            id = entityId,
            lotteryParticipant = Set(participant)
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(participant)


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
          val participantId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = participantId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString
          )

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId), clock)
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
              participant = participant
            )
          )

          val ballotAddedEvent = ParticipantAddedEvent(
            id = entityId,
            lotteryParticipant = Set(participant)
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(participant)


          val successfulCloseResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.CloseResult](
            LotteryEntity.CloseLotteryCommand()
          )

          successfulCloseResult.event shouldBe a[ClosedLotteryEvent]
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].id shouldBe createdEvent.id
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].lotteryName shouldBe lotteryName
          successfulCloseResult.stateOfType[LotteryEntity.ClosedState].createdAt shouldBe createdEvent.createdAt


          val unsupportedCreateResult = eventSourcedBehaviorTestKit.runCommand[LotteryEntity.ParticipateResult](
            LotteryEntity.ParticipateCommand(participant)
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
          val participantId = UUID.randomUUID().toString

          val participant = Participant(
            participantId = participantId,
            participantFirstName = Random.alphanumeric.take(12).mkString,
            participantLastName = Random.alphanumeric.take(12).mkString
          )

          val createdEvent = CreatedLotteryEvent(
            id = entityId,
            lotteryName = lotteryName,
            createdAt = LocalDate.now(clock)
          )

          val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit[LotteryEntity.Command, LotteryEntity.Event, LotteryEntity.State](
            system = system,
            behavior = LotteryEntity(entityId, PersistenceId.ofUniqueId(entityId), clock)
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
              participant = participant
            )
          )

          val ballotAddedEvent = ParticipantAddedEvent(
            id = entityId,
            lotteryParticipant = Set(participant)
          )

          successfulParticipateResult.event shouldBe ballotAddedEvent
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].id shouldBe createdEvent.id
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].lotteryName shouldBe lotteryName
          successfulParticipateResult.stateOfType[LotteryEntity.ActiveState].createdAt shouldBe createdEvent.createdAt
          successfulParticipateResult.reply shouldBe LotteryEntity.SuccessfulParticipateResult(participant)


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
  }
}
