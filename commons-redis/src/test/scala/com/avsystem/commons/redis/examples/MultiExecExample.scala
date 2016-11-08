package com.avsystem.commons
package redis.examples

import akka.actor.ActorSystem
import akka.util.Timeout
import com.avsystem.commons.misc.Opt
import com.avsystem.commons.redis._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Example that shows execution of simple `MULTI`-`EXEC` blocks. For full Redis transactions with optimistic locking
  * and `WATCH` command, see [[TransactionExample]].
  */
object MultiExecExample extends App {
  implicit val actorSystem = ActorSystem()
  implicit val timeout: Timeout = 10.seconds

  // Executing MULTI-EXEC blocks (without WATCH) is very similar to pipelining.
  // See PipeliningExample for more details.

  val api = RedisApi.Batches.StringTyped
  val executor: RedisExecutor = new RedisNodeClient

  // In order to send some commands in a MULTI-EXEC block, create a RedisBatch and simply mark it as `transaction`.
  val transaction: RedisBatch[(Opt[String], Long)] =
    (api.get("key"), api.incr("otherKey")).sequence.transaction // forces execution inside MULTI-EXEC block

  // This will send following commands to Redis:
  //   MULTI
  //   GET key
  //   INCR otherKey
  //   EXEC
  executor.executeBatch(transaction).onComplete {
    case Success((Opt(textValue), numericValue)) => println(s"Got $textValue and $numericValue")
    case Success((Opt.Empty, numericValue)) => println(s"Got only $numericValue")
    case Failure(t) => t.printStackTrace()
  }

  // If you simply need your batch to be atomic, you can also use `atomic` instead of `transaction`.
  // The difference is that single-command batches are not wrapped in MULTI-EXEC block because
  // single command is always atomic by itself.
}