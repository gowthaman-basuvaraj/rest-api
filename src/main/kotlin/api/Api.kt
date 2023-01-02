package api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import db.DB
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.HttpStatus
import io.javalin.json.JavalinJackson

object Api {
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
            path("/api") {
                path("/{resource}") {
                    before {
                        DB.checkAndCreate(it.pathParam("resource"))
                    }
                    get("") {
                        it.json(
                            DB.getRows(it.pathParam("resource"))
                        )
                    }
                    post("") {
                        val body = it.body()
                        DB.save(it.pathParam("resource"), body)
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