package infrastructure

import akka.japi.function
import akka.projection.jdbc.JdbcSession
import akka.stream.alpakka.slick.scaladsl.SlickSession

import java.sql.Connection
import scala.util.Try

class SlickJdbcSession(slickSession: SlickSession) extends JdbcSession {
  private[this] lazy val connection =
    slickSession.db.source.createConnection()

  override def withConnection[Result](func: function.Function[Connection, Result]): Result =
    func(connection)

  override def commit(): Unit =
    connection.commit()

  override def rollback(): Unit =
    connection.rollback()

  override def close(): Unit =
    connection.close()

}
