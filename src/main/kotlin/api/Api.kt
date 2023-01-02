package api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import db.DB
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.NotFoundResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.json.JavalinJackson
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import java.util.Date

object Api {
    private const val DAYS_30 = 30 * 24 * 60 * 60 * 1000L
    private val logger = LoggerFactory.getLogger("Api")

    @JvmStatic
    fun main(args: Array<String>) {
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
                    DB.checkAndCreate("user_authenticate")
                }
                post("/create") {
                    val user = it.formParam("user") ?: throw BadRequestResponse()
                    val auth = it.formParam("auth") ?: throw BadRequestResponse()

                    DB.save(
                        "user_authenticate", mapOf(
                            "user" to user,
                            "auth" to auth
                        )
                    )
                }
                post("/validate") {
                    val user = it.formParam("user") ?: throw BadRequestResponse()
                    val auth = it.formParam("auth") ?: throw BadRequestResponse()
                    if (DB.authenticate("user_authenticate", user, auth)) {

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
                        val at = it.header("Authorization")?.replace("Bearer ", "")?.trim()
                            ?: throw UnauthorizedResponse("auth token is missing")

                        val cl = Jwts.parser().parse(at).body as Map<String, Any>
                        logger.warn("access by ${it.method()} ${it.url()} by ${cl["user"]}")
                        DB.checkAndCreate(it.pathParam("resource"))
                    }
                    get("/{id}") {
                        it.json(
                            DB.getRow(it.pathParam("resource"), it.pathParam("id")) ?: throw NotFoundResponse()
                        )
                    }
                    get("") {
                        val q = it.queryParam("q") ?: "{}"
                        val params = om.readValue<Map<String, Any>>(q)
                        it.json(
                            DB.getRows(it.pathParam("resource"), filters = params)
                        )
                    }
                    post("") {
                        val m = om.readValue<Map<String, Any>>(it.body())
                        DB.save(it.pathParam("resource"), m)
                        it.status(HttpStatus.ACCEPTED)
                    }
                    delete("/{id}") {
                        DB.delete(it.pathParam("resource"), it.pathParam("id"))
                    }
                    put("/{id}") {
                        val m = om.readValue<Map<String, Any>>(it.body())
                        DB.update(it.pathParam("resource"), it.pathParam("id"), m)
                    }
                }
            }
        }

        app.start()

    }
}