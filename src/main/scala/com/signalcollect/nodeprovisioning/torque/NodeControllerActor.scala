/*
 *  @author Philip Stutz
 *  
 *  Copyright 2012 University of Zurich
 *      
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *         http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */

package com.signalcollect.nodeprovisioning.torque

import com.signalcollect.nodeprovisioning.Node
import scala.util.Random
import java.io.File
import scala.sys.process._
import org.apache.commons.codec.binary.Base64
import com.signalcollect.implementations.serialization.DefaultSerializer
import java.io.FileOutputStream
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import ch.ethz.ssh2.StreamGobbler
import java.io.FileInputStream
import java.io.File
import ch.ethz.ssh2.Connection
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor
import com.typesafe.config.ConfigFactory
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import akka.util.Duration
import akka.util.duration._
import java.util.concurrent.TimeUnit
import akka.dispatch.Future
import akka.dispatch.Await
import akka.actor.PoisonPill
import com.signalcollect.nodeprovisioning.NodeProvisioner
import com.signalcollect.configuration.AkkaConfig
import com.signalcollect.interfaces.Request
import com.signalcollect.configuration.EventBased
import com.signalcollect.configuration.GraphConfiguration
import com.signalcollect.configuration.Pinned

class NodeControllerActor(nodeId: Int, nodeProvisionerAddress: String) extends Actor with Node {

  var nodeProvisioner: ActorRef = _

  def createWorker(workerId: Int, numberOfWorkers: Int, config: GraphConfiguration): ActorRef = {
    println("Received create worker request for worker with id: " + workerId)
    val workerName = "Worker" + workerId
    val system = ActorSystem("SignalCollect")
    config.akkaDispatcher match {
      case EventBased => system.actorOf(Props(config.workerFactory.createInstance(workerId, numberOfWorkers, config)), name = workerName)
      case Pinned => system.actorOf(Props().withCreator(config.workerFactory.createInstance(workerId, numberOfWorkers, config)).withDispatcher("akka.actor.pinned-dispatcher"), name = workerName)
    }
  }

  def numberOfCores = Runtime.getRuntime.availableProcessors

  override def preStart() = {
    println("NodeControllerActor running: " + nodeId)
    println("Finding NodeProvisioner.")
    nodeProvisioner = context.actorFor(nodeProvisionerAddress)
    println("Sending message to NodeProvisioner.")
    nodeProvisioner ! "NodeReady"
    println("Sent.")
  }

  def receive = {
    case Request(command, reply) =>
      println("Received command: " + command)
      val result = command(this)
      if (reply) {
        sender ! result
      }
    case other =>
      println("Received unexpected message from " + sender + ": " + other)
  }

}