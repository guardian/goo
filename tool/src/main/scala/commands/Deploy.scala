package goo.deploy

import com.ning.http.client.Response
import commands.S3
import dispatch._
import goo.{Command, Config, GooSubCommandHandler, Stage}
import org.joda.time.DateTime
import org.kohsuke.args4j.spi.{SubCommand, SubCommands}
import org.kohsuke.args4j.{Argument, Option => option}
import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

class DeployCommand() extends Command with Stage {
  override def executeImpl() {
    println(s"${Console.RED}'deploy' command has been deprecated. Use riff-raff (project 'dotcom:all') to deploy the guardian frontend apps.")
  }
}

