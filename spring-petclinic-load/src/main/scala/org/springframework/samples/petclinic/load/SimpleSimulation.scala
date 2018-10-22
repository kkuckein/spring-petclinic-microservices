package org.springframework.samples.petclinic.load

import scala.language.postfixOps
import io.gatling.core.scenario.Simulation
import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class SimpleSimulation extends Simulation {

  val httpConfig = http.baseURL("http://api-gateway:8080")

  val ownerIds = Iterator.continually(
    // Random number will be accessible in session under variable "OrderRef"
    Map("ownerId" -> (1 + Random.nextInt(10)))
  )

  val scenarioA = scenario("View Vets").feed(ownerIds).pause(5 minutes)
    .forever {
      pace(10 seconds)
        .exec {
          http("Home").get("/")
        }
        .exec {
          http("All Owners").get("/api/customer/owners")
        }
        .exec {
          http("Owner Details").get("/api/gateway/owners/${ownerId}")
        }
    }

  setUp(scenarioA.inject(atOnceUsers(5))).protocols(httpConfig)

}
