package db

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.http.sse.SseClient
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap


class Database(private val db: String = "app.db") {
    /*
    What we Do Here?
    1. Open a Sqlite DB
    2. when we ask for resource i.e a table,
        2.1 check if it exists
        2.1 if no, create it with just 2 cols (id int, data json)
    3. run query
    4. create entry
    5. delete entry
    6. update entry
    */

    private val om = jacksonObjectMapper()
    private val clients = arrayListOf<SseClient>()


    private val logger = LoggerFactory.getLogger("DB")
    private val connection: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$db")
    }
    private val tableCache = ConcurrentHashMap<String, Boolean>()

    fun checkAndCreate(resource: String) {
        tableCache.computeIfAbsent(resource) {
            connection.createStatement()
                .use { ps ->

                    ps.execute("SELECT count(*) as table_count FROM sqlite_master WHERE type='table' AND name='$resource'")
                    val resultSet = ps.resultSet
                    if (resultSet.next()) {

                        val tblCount = resultSet.getInt("table_count")
                        logger.warn("Check for table $resource => $tblCount")
                        if (tblCount == 0) {

                            connection.createStatement().use {
                                it.execute("create table `$resource` (id integer primary key autoincrement, data json)")
                            }
                        }
                    }

                }

            true
        }
    }

    fun getRows(resource: String, filters: Map<String, Any> = mapOf()): List<Map<String, Any>> {
        val results = arrayListOf<Map<String, Any>>()


        val filterSql = arrayListOf<String>()
        val filterValues = arrayListOf<Any>()

        val whereClause = if (filters.isNotEmpty()) {
            filters.entries.forEachIndexed { index, entry ->
                filterSql.add("data->>'${entry.key}' = :${index + 1}")
                filterValues.add(entry.value)
            }

            "where " + filterSql.joinToString(" and ")
        } else ""

        val baseSql = "select id, data from `$resource` $whereClause".trim()

        logger.warn("QueryStr -> $baseSql with Params[$filterValues]")

        connection.prepareStatement(baseSql).use {

            filterValues.forEachIndexed { idx, any ->
                val index = idx + 1
                //a very rudimentary check DOES NOT SUPPORT ANY PROPER Filters
                if (any is Number) {
                    it.setInt(index, any.toInt())
                } else {
                    it.setString(index, any.toString())
                }
            }

            if (it.execute()) {
                val resultSet = it.resultSet
                while (resultSet.next()) {
                    results.add(makeResult(resultSet))
                }
            }
        }

        return results
    }

    private fun makeResult(resultSet: ResultSet): Map<String, Any> {

        val id = resultSet.getInt("id")
        val content = resultSet.getString("data")

        val data = om.readValue<Map<String, Any>>(content).toMutableMap()
        data["id"] = id

        return data
    }

    fun authenticate(resource: String, user: String, auth: String): Boolean {
        connection.prepareStatement("select * from `$resource` where data->>'user' = :1 and data->>'auth' = :2")
            .use {
                it.setString(1, user)
                it.setString(2, auth)
                it.execute()
                val rs = it.resultSet
                if (rs.next()) {
                    return true
                }
            }

        return false
    }

    fun getRow(resource: String, id: String): Map<String, Any>? {
        connection.prepareStatement("select * from `${resource}` where id = :1").use {
            it.setString(1, id)
            it.execute()
            val rs = it.resultSet
            if (rs.next()) {
                val content = rs.getString("data")

                return om.readValue<Map<String, Any>>(content)
            }
        }

        return null
    }


    fun save(resource: String, body: Map<String, Any>) {
        connection.prepareStatement("insert into `$resource`(data) values(json(:1))")
            .use {
                it.setString(1, om.writeValueAsString(body))
                it.execute()
            }
        notifyUI(resource, "save")

    }

    fun delete(resource: String, id: String) {
        connection.prepareStatement("delete from `${resource}` where id = :1").use {
            it.setString(1, id)
            it.execute()
        }
        notifyUI(resource, "delete")

    }

    fun update(resource: String, id: String, m: Map<String, Any?>) {
        connection.prepareStatement("select data from `${resource}` where id = :1").use { ps1 ->
            ps1.setString(1, id)
            ps1.execute()

            val rs = ps1.resultSet
            if (rs.next()) {
                val content = rs.getString("data")

                val data = om.readValue<Map<String, Any>>(content).toMutableMap()
                logger.warn("DB[$data] UI[$m]")

                //remove all keys where the values are null, put the rest into data
                m.entries.forEach { (k, v) ->
                    if (v == null) {
                        data.remove(k)
                    } else {
                        data.put(k, v)
                    }
                }

                logger.warn("Final[$data]")
                connection.prepareStatement("update '${resource}' set data = json(:1) where id = (:2)")
                    .use { ps2 ->
                        ps2.setString(1, om.writeValueAsString(data))
                        ps2.setString(2, id)
                        ps2.execute()
                    }
            }

        }
        notifyUI(resource, "update")
    }

    private fun notifyUI(resource: String, action: String) {
        val data = om.writeValueAsString(
            mapOf(
                "id" to resource,
                "status" to true,
                "action" to action
            )
        )
        logger.warn("send update $resource => ${clients.size}, data")
        for (client in clients) {
            try {
                logger.warn("send update $resource => $client")

                client.sendEvent("updates", data)
            } catch (e: Exception) {
                //ignore
                e.printStackTrace()
            }
        }
    }

    fun addClient(it: SseClient) {
        logger.warn("Added New Client $it")
        it.keepAlive()
        it.sendEvent("welcome", "to update stream")
        clients.add(it)
    }

}