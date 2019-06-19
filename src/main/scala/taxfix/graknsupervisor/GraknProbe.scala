package taxfix.graknsupervisor

import java.util.concurrent.TimeUnit

import scala.concurrent.{ExecutionContext, Future}
import graql.lang.Graql._
import scala.concurrent.duration._
import scala.language.postfixOps
import grakn.client.GraknClient

object GraknProbe {

  def start()(implicit ec: ExecutionContext) = Future {
    Thread.sleep((1 minute).toMillis)
    while(true) {
      check()
      Thread.sleep((20 seconds).toMillis)
    }
  }

  // this will throw an exception if Grakn is unhealthy
  def check() = {
    val client = new GraknClient("localhost:48555")
    val session = client.session("grakn_probe")

    val readTransaction = session.transaction.read()
    val getQuery = `match`(`var`("x").isa("thing")).get.limit(1)
    readTransaction.stream(getQuery)

    readTransaction.close()
    session.close()
    client.close()
  }
}
