package io.constellation.lsp

import cats.effect.{IO, Ref}
import cats.implicits._
import io.constellation.{CValue, DagSpec, Module, SteppedExecution}

import java.util.UUID
import scala.concurrent.duration._

/**
 * Manages debug/stepping sessions for the language server.
 * Thread-safe session storage with automatic cleanup of stale sessions.
 */
class DebugSessionManager private (
  sessionsRef: Ref[IO, Map[String, DebugSessionManager.ManagedSession]]
) {
  import DebugSessionManager._

  /** Session timeout - sessions are cleaned up after this duration of inactivity */
  private val sessionTimeout: FiniteDuration = 30.minutes

  /**
   * Create a new debug session.
   */
  def createSession(
    dagSpec: DagSpec,
    syntheticModules: Map[UUID, Module.Uninitialized],
    registeredModules: Map[UUID, Module.Uninitialized],
    inputs: Map[String, CValue]
  ): IO[SteppedExecution.SessionState] = {
    val sessionId = UUID.randomUUID().toString

    for {
      session <- SteppedExecution.createSession(
        sessionId,
        dagSpec,
        syntheticModules,
        registeredModules,
        inputs
      )

      // Initialize the runtime
      initializedSession <- SteppedExecution.initializeRuntime(session)

      managed = ManagedSession(
        session = initializedSession,
        lastAccessed = System.currentTimeMillis()
      )

      _ <- sessionsRef.update(_ + (sessionId -> managed))
      _ <- cleanupStaleSessions()

    } yield initializedSession
  }

  /**
   * Get a session by ID, updating last accessed time.
   */
  def getSession(sessionId: String): IO[Option[SteppedExecution.SessionState]] = {
    sessionsRef.modify { sessions =>
      sessions.get(sessionId) match {
        case Some(managed) =>
          val updated = managed.copy(lastAccessed = System.currentTimeMillis())
          (sessions + (sessionId -> updated), Some(managed.session))
        case None =>
          (sessions, None)
      }
    }
  }

  /**
   * Update a session with new state.
   */
  def updateSession(session: SteppedExecution.SessionState): IO[Unit] = {
    sessionsRef.update { sessions =>
      sessions.get(session.sessionId) match {
        case Some(_) =>
          sessions + (session.sessionId -> ManagedSession(
            session = session,
            lastAccessed = System.currentTimeMillis()
          ))
        case None =>
          sessions
      }
    }
  }

  /**
   * Remove a session.
   */
  def removeSession(sessionId: String): IO[Boolean] = {
    sessionsRef.modify { sessions =>
      if (sessions.contains(sessionId)) {
        (sessions - sessionId, true)
      } else {
        (sessions, false)
      }
    }
  }

  /**
   * Execute the next batch for a session.
   */
  def stepNext(sessionId: String): IO[Option[(SteppedExecution.SessionState, Boolean)]] = {
    for {
      maybeSession <- getSession(sessionId)
      result <- maybeSession match {
        case Some(session) =>
          SteppedExecution.executeNextBatch(session).flatMap { case (updatedSession, isComplete) =>
            updateSession(updatedSession).as(Some((updatedSession, isComplete)))
          }
        case None =>
          IO.pure(None)
      }
    } yield result
  }

  /**
   * Execute to completion for a session.
   */
  def stepContinue(sessionId: String): IO[Option[SteppedExecution.SessionState]] = {
    for {
      maybeSession <- getSession(sessionId)
      result <- maybeSession match {
        case Some(session) =>
          for {
            completedSession <- SteppedExecution.executeToCompletion(session)
            _ <- updateSession(completedSession)
          } yield Some(completedSession)
        case None =>
          IO.pure(None)
      }
    } yield result
  }

  /**
   * Stop a session (remove it).
   */
  def stopSession(sessionId: String): IO[Boolean] = {
    removeSession(sessionId)
  }

  /**
   * Remove sessions that haven't been accessed within the timeout.
   */
  private def cleanupStaleSessions(): IO[Unit] = {
    val cutoff = System.currentTimeMillis() - sessionTimeout.toMillis
    sessionsRef.update { sessions =>
      sessions.filter { case (_, managed) =>
        managed.lastAccessed > cutoff
      }
    }
  }

  /**
   * Get count of active sessions (for monitoring).
   */
  def sessionCount: IO[Int] = {
    sessionsRef.get.map(_.size)
  }
}

object DebugSessionManager {

  /** Internal session wrapper with metadata */
  case class ManagedSession(
    session: SteppedExecution.SessionState,
    lastAccessed: Long
  )

  /**
   * Create a new DebugSessionManager.
   */
  def create: IO[DebugSessionManager] = {
    Ref.of[IO, Map[String, ManagedSession]](Map.empty).map { ref =>
      new DebugSessionManager(ref)
    }
  }
}
