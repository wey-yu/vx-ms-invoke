/**
  * Created by k33g on 06/05/2017.
  */


import sun.net.www.http.HttpClient

import io.vertx.core.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{ServiceDiscovery, ServiceDiscoveryOptions}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Invoke {
  def main(args: Array[String]) {

    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    val redisHost = sys.env.get("REDIS_HOST") match {
      case None => "127.0.0.1"
      case Some(host) => host
    }
    val redisPort = sys.env.get("REDIS_PORT") match {
      case None => 6379
      case Some(port) => port.toInt
    }
    val redisAuth = sys.env.get("REDIS_PASSWORD") match {
      case None => null
      case Some(auth) => auth
    }
    val redisRecordsKey = sys.env.get("REDIS_RECORDS_KEY") match {
      case None => "scala-records"
      case Some(key) => key
    }


    val discovery = ServiceDiscovery.create(vertx, ServiceDiscoveryOptions()
      .setBackendConfiguration(
        new JsonObject()
          .put("host", redisHost)
          .put("port", redisPort)
          .put("auth", redisAuth)
          .put("key", redisRecordsKey)
      )
    )


    //discovery.close()

    val httpPort = sys.env.get("PORT") match { // internal port has to be set to 8080 on CC
      case None => 8080
      case Some(port) => port.toInt
    }

    router.route("/yo").handler(context => {

      discovery.getRecordFuture(new JsonObject().put("name", "salutations-prod")).onComplete{
        case Success(result) => {
          val reference = discovery.getReference(result)

          val client = reference.getAs(classOf[io.vertx.scala.ext.web.client.WebClient])

          client.get("/api/yo").sendFuture().onComplete{
            case Success(result) => {

              context
                .response()
                .putHeader("content-type", "application/json;charset=UTF-8")
                .end(result.body())

            }
            case Failure(cause) => {
              context
                .response()
                .putHeader("content-type", "application/json;charset=UTF-8")
                .end(new JsonObject().put("error", cause).encodePrettily())
            }
          }
        }
        case Failure(cause) => {
          context
            .response()
            .putHeader("content-type", "application/json;charset=UTF-8")
            .end(new JsonObject().put("error", cause).encodePrettily())
        }
      }

    })

    router.get("/hi").handler(context => {

      discovery.getRecordFuture(new JsonObject().put("name", "salutations-prod")).onComplete{
        case Success(result) => {
          val reference = discovery.getReference(result)
          val client = reference.getAs(classOf[io.vertx.scala.ext.web.client.WebClient])

          client.get("/api/hi").sendFuture().onComplete{
            case Success(result) => {

              context
                .response()
                .putHeader("content-type", "application/json;charset=UTF-8")
                .end(result.body())

            }
            case Failure(cause) => {
              context
                .response()
                .putHeader("content-type", "application/json;charset=UTF-8")
                .end(new JsonObject().put("error", cause).encodePrettily())
            }
          }
        }
        case Failure(cause) => {
          context
            .response()
            .putHeader("content-type", "application/json;charset=UTF-8")
            .end(new JsonObject().put("error", cause).encodePrettily())
        }
      }

    })

    router.get("/").handler(context => {

      context
        .response()
        .putHeader("content-type", "text/html;charset=UTF-8")
        .end("<h1>Hello 🌍</h1>")
    })

    println(s"🌍 Listening on $httpPort - Enjoy 😄")
    server.requestHandler(router.accept _).listen(httpPort)

  }
}