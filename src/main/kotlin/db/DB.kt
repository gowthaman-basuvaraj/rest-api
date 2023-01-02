package db

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.json.JavalinJackson
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

import java.sql.SQLException
import java.sql.SQLType


object DB {
    /*
    What we Do Here?
    1. Open an Sqlite DB
    2. when we ask for resource, i.e. a table we check if it exists
        2.1 if no, create it with just 2 cols (id int, data json)
    3. run query
    4. create entry
    5. delete entry
    6. update entry
    */

    private const val db = "app.db"
    private val om = jacksonObjectMapper()

    private val logger = LoggerFactory.getLogger("DB")
    private val connection: Connection by lazy {
        DriverManager.getConnection("jdbc:sqlite:$db")
    }

    fun checkAndCreate(resource: String) {
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

    }

    fun getRows(resource: String): List<Map<String, Any>> {
        val results = arrayListOf<Map<String, Any>>()

        connection.createStatement().use {
            if (it.execute("select id, data from `$resource`")) {
                val resultSet = it.resultSet
                while (resultSet.next()) {
                    results.add(
                        makeResult(resultSet)
                    )
                }
            }
        }
        return results
    }

    private fun makeResult(resultSet: ResultSet): Map<String, Any> {

        val id = resultSet.getInt("id")
        val content = resultSet.getString("data")

        val data = om.readValue<Map<String, Any>>(content)

        return mapOf(
            "id" to id,
            "data" to data
        )
    }

    fun save(resource: String, body: String) {
        connection.prepareStatement("insert into `$resource`(data) values(json(:1))")
            .use {
                it.setString(1, body)
                it.execute()
            }
    }

    fun delete(resource: String, id: String) {
        connection.prepareStatement("delete from `${resource}` where id = :1").use {
            it.setString(1, id)
            it.execute()
        }
    }

    fun update(resource: String, id: String, m: Map<String, Any>) {
        connection.prepareStatement("select data from `${resource}` where id = :1").use { ps1 ->
            ps1.setString(1, id)
            ps1.execute()

            val rs = ps1.resultSet
            if (rs.next()) {
                val content = rs.getString("data")

                val data = om.readValue<Map<String, Any>>(content).toMutableMap()
                logger.warn("DB[$data] UI[$m]")

                data.putAll(m)


                logger.warn("DB[$data] UI[$m] Final[$data]")
                connection.prepareStatement("update '${resource}' set data = json(:1) where id = (:2)")
                    .use { ps2 ->
                        ps2.setString(1, om.writeValueAsString(data))
                        ps2.setString(2, id)
                        ps2.execute()
                    }
            }

        }
    }

    class DBError(msg: String) : Throwable(msg)
}