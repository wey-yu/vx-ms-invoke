import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.client.WebClient

import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{ServiceDiscovery, ServiceDiscoveryOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Invoke {

  val vertx = Vertx.vertx()

  def discovery: ServiceDiscovery = {
    // Settings for the Redis backend
    val redisHost = sys.env.get("REDIS_HOST").getOrElse("127.0.0.1")
    val redisPort = sys.env.get("REDIS_PORT").getOrElse("6379").toInt
    val redisAuth = sys.env.get("REDIS_PASSWORD").getOrElse(null)
    val redisRecordsKey = sys.env.get("REDIS_RECORDS_KEY").getOrElse("scala-records")

    // Mount the service discovery backend (Redis)
    val discovery = ServiceDiscovery.create(vertx, ServiceDiscoveryOptions()
      .setBackendConfiguration(
        new JsonObject()
          .put("host", redisHost)
          .put("port", redisPort)
          .put("auth", redisAuth)
          .put("key", redisRecordsKey)
      )
    )
    // TODO: retry when failure
    // discovery.close() // or not
    return discovery
  }

  def webServer(router: Router, client: WebClient) = {
    val server = vertx.createHttpServer()
    val httpPort = sys.env.get("PORT").getOrElse("8081").toInt

    router.get("/hi").handler(context => {
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
    })

    router.get("/yo").handler(context => {
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
    })

    router.route("/").handler(context => {
      context
        .response()
        .putHeader("content-type", "text/html;charset=UTF-8")
        .end("<h1>Hello 🌍 I'm calling microservice(s)</h1>")
    })

    println(s"🌍 Listening on $httpPort - Enjoy 😄")
    server.requestHandler(router.accept _).listen(httpPort)

  }

  def main(args: Array[String]): Unit = {

    val router = Router.router(vertx)
    val discoveryService = discovery

    val serviceName = sys.env.get("SERVICE_NAME").getOrElse("hello")
    // search service by name
    discoveryService.getRecordFuture(new JsonObject().put("name", serviceName)).onComplete{
      case Success(result) => {
        val reference = discoveryService.getReference(result)
        val client = reference.getAs(classOf[WebClient])
        webServer(router, client)
      }
      case Failure(cause) => {
        //TODO
      }
    }
  } // end of main
}
