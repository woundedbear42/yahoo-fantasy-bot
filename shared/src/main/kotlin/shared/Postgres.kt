/*
 * MIT License
 *
 * Copyright (c) 2020 Landon Patmore
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package shared

import com.github.scribejava.core.model.OAuth2AccessToken
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


class Postgres : IDatabase {
    private var connection: Connection? = null

    override fun startupMessageSent(): Boolean {
        return try {
            getConnection()

            val statement = connection!!.createStatement()
            val row =
                statement.executeQuery("SELECT * FROM start_up_message_received ORDER BY \"was_received\" DESC LIMIT 1")

            if (row.next()) {
                row.getBoolean("was_received")
            } else false

        } catch (e: SQLException) {
            println(e.message)
            false
        }
    }

    override fun latestTimeChecked(): Long {
        dropTopRows("latest_time", "latest_time")
        return try {
            getConnection()

            val statement = connection!!.createStatement()
            val row =
                statement.executeQuery("SELECT * FROM latest_time ORDER BY \"latest_time\" DESC LIMIT 1")

            if (row.next()) {
                row.getLong("latest_time")
            } else System.currentTimeMillis() / 1000

        } catch (e: SQLException) {
            println(e.message)
            System.currentTimeMillis() / 1000
        }
    }

    override fun latestTokenData(): Pair<Long, OAuth2AccessToken>? {
        dropTopRows("tokens", "yahooTokenRetrievedTime")

        try {
            getConnection()

            val statement = connection!!.createStatement()
            val row =
                statement.executeQuery("SELECT * FROM tokens ORDER BY \"yahooTokenRetrievedTime\" DESC LIMIT 1")

            if (row.next()) {
                val refreshToken = row.getString("yahooRefreshToken")
                val retrievedTime = row.getLong("yahooTokenRetrievedTime")
                val rawResponse = row.getString("yahooTokenRawResponse")
                val tokenType = row.getString("yahooTokenType")
                val accessToken = row.getString("yahooAccessToken")
                val expiresIn = row.getLong("yahooTokenExpireTime")
                val scope = row.getString("yahooTokenScope")

                return Pair(
                    retrievedTime,
                    OAuth2AccessToken(
                        accessToken,
                        tokenType,
                        expiresIn.toInt(),
                        refreshToken,
                        scope,
                        rawResponse
                    )
                )
            }
            return null
        } catch (e: SQLException) {
            println(e.message)
            return null
        }
    }

    /**
     * Gets the connection to the DB.  Loops indefinitely.
     *
     * @return connection to DB
     */
    private fun getConnection(): Connection? {
        while (connection == null) {
            try {
                println("Connection does not exist to database.  Creating...")

                connection =
                    DriverManager.getConnection(EnvVariable.Str.JdbcDatabaseUrl.variable)

                println("Connection established to database.")

                return connection
            } catch (e: SQLException) {
                println(e.message)
                try {
                    Thread.sleep(5000)
                } catch (e1: InterruptedException) {
                    println(e.message)
                }
            }
        }

        return connection
    }

    override fun saveTokenData(token: OAuth2AccessToken) {
        try {
            getConnection()

            println("Attempting to save token data...")

            val refreshToken = token.refreshToken
            val retrievedTime = System.currentTimeMillis().toString()
            val rawResponse = token.rawResponse
            val tokenType = token.tokenType
            val accessToken = token.accessToken
            val expiresIn = token.expiresIn!!.toString()
            val scope = token.scope

            val statement = connection!!.createStatement()
            val sql =
                "INSERT INTO tokens (\"yahooRefreshToken\",\"yahooTokenRetrievedTime\",\"yahooTokenRawResponse\",\"yahooTokenType\",\"yahooAccessToken\", \"yahooTokenExpireTime\", \"yahooTokenScope\") VALUES (\'$refreshToken\',\'$retrievedTime\',\'$rawResponse\',\'$tokenType\',\'$accessToken\',\'$expiresIn\',$scope)"

            statement.executeUpdate(sql)

            println("Token data has been saved.")
        } catch (e: SQLException) {
            println(e.message)
        }
    }

    override fun saveLastTimeChecked() {
        try {
            getConnection()

            println("Attempting to save last time checked...${System.currentTimeMillis() / 1000}")

            val statement = connection!!.createStatement()
            val sql =
                "INSERT INTO latest_time (\"latest_time\")" + " VALUES (\'" + System.currentTimeMillis() / 1000 + "\')"

            statement.executeUpdate(sql)

            println("Latest time has been saved.")
        } catch (e: SQLException) {
            println(e.message)
        }

    }

    override fun markStartupMessageReceived() {
        try {
            getConnection()

            println("Marking startup message sent...")

            val statement = connection!!.createStatement()
            val sql =
                "INSERT INTO start_up_message_received (\"was_received\")" + " VALUES (\'" + true + "\')"

            statement.executeUpdate(sql)

            println("Startup message marked sent.")
        } catch (e: SQLException) {
            println(e.message)
        }
    }

    /**
     * Drops the top rows of a given table.
     *
     * @param tableName table name
     * @param orderBy   the column to order by
     */
    private fun dropTopRows(tableName: String, orderBy: String) {
        try {
            getConnection()
            val statement = connection!!.createStatement()
            val row = statement.executeQuery("SELECT COUNT(*) FROM $tableName")

            if (row.next()) {
                val count = row.getInt(1)

                if (count > 20) {
                    println("More than 20 entries in the $tableName table.  Removing top 20.")
                    statement.execute(
                        "DELETE\n" +
                                "FROM " + tableName + "\n" +
                                "WHERE ctid IN (\n" +
                                "        SELECT ctid\n" +
                                "        FROM " + tableName + "\n" +
                                "        ORDER BY \"" + orderBy + "\" limit 20\n" +
                                "        )"
                    )
                }
            }

        } catch (e: SQLException) {
            println(e.message)
        }

    }
}
