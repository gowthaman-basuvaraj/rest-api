package api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import db.Database
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.*
import io.javalin.http.sse.SseClient
import io.javalin.json.JavalinJackson
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import java.util.Date

object Api {
    private const val DAYS_30 = 30 * 24 * 60 * 60 * 1000L
    private val logger = LoggerFactory.getLogger("Api")

    class Server : CliktCommand() {
        private val port: Int by option(help = "port number to run").int().required()
        private val dbName: String by option().default("app.db")
        override fun run() {
            val db = Database(dbName)
            val om = jacksonObjectMapper()
            val app = Javalin.create { cfg ->
                cfg.plugins.enableCors { cors ->
                    cors.add {
                        it.anyHost()
                    }
                }
                cfg.jsonMapper(JavalinJackson(om))
            }

            app.routes {
                path("/auth") {
                    before {
                        db.checkAndCreate("user_authenticate")
                    }
                    post("/create") { ctx ->
                        val user = ctx.formParam("user") ?: throw BadRequestResponse()
                        val auth = ctx.formParam("auth") ?: throw BadRequestResponse()

                        val existingUsers = db.getRows("user_authenticate")
                            .filter { it.containsKey("user") }
                            .map { it["user"] as String }

                        if (existingUsers.contains(user)) throw BadRequestResponse("user already exists")

                        db.save(
                            "user_authenticate", mapOf(
                                "user" to user,
                                "auth" to auth
                            )
                        )

                        ctx.json(
                            mapOf(
                                "status" to true
                            )
                        )
                    }
                    post("/validate") {
                        val user = it.formParam("user") ?: throw BadRequestResponse()
                        val auth = it.formParam("auth") ?: throw BadRequestResponse()
                        if (db.authenticate("user_authenticate", user, auth)) {

                            it.json(
                                mapOf(
                                    "authToken" to Jwts.builder().addClaims(
                                        mapOf(
                                            "user" to user
                                        )
                                    ).setExpiration(Date(Date().time + DAYS_30)).compact()
                                )
                            )
                        }
                    }
                }
                path("/api") {

                    path("/{resource}") {
                        before {
                            //ignore options
                            if (it.method() == HandlerType.OPTIONS) return@before

                            val at = it.header("Authorization")?.replace("Bearer ", "")?.trim()
                                ?: throw UnauthorizedResponse("auth token is missing")

                            val cl = Jwts.parser().parse(at).body as Map<*, *>
                            logger.warn("access by ${it.method()} ${it.url()} by ${cl["user"]}")
                            db.checkAndCreate(it.pathParam("resource"))
                        }
                        get("/{id}") {
                            it.json(
                                db.getRow(it.pathParam("resource"), it.pathParam("id")) ?: throw NotFoundResponse()
                            )
                        }
                        get("") {
                            val q = it.queryParam("q") ?: "{}"
                            val params = om.readValue<Map<String, Any>>(q)
                            it.json(
                                db.getRows(it.pathParam("resource"), filters = params)
                            )
                        }
                        post("") {
                            val m = om.readValue<Map<String, Any>>(it.body())
                            db.save(it.pathParam("resource"), m)
                            it.status(HttpStatus.ACCEPTED)
                        }
                        delete("/{id}") {
                            db.delete(it.pathParam("resource"), it.pathParam("id"))
                        }
                        patch("/{id}") {
                            val m = om.readValue<Map<String, Any?>>(it.body())
                            db.update(it.pathParam("resource"), it.pathParam("id"), m)
                        }
                    }

                }
                sse("/updates") {
                    db.addClient(it)
                }
            }

            app.start(port)

        }

    }

    @JvmStatic
    fun main(args: Array<String>) = Server().main(args)
}