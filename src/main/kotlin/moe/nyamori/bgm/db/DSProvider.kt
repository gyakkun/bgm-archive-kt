package moe.nyamori.bgm.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File

class DSProvider(
    private val jdbcUrl: String,
    jdbcUsername: String?,
    jdbcPassword: String?,
    hikariMinIdle: Int,
    hikariMaxConn: Int,
    dbIsEnableWal: Boolean,
) {
    val isSqlite = jdbcUrl.lowercase().contains("sqlite")
    val sqliteFilePathOrNull: String?
        get() {
            if (!isSqlite) return null
            return this.jdbcUrl.substring("jdbc:sqlite:".length)
        }
    val ds: HikariDataSource by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        if (isSqlite) {
            val sqliteFilePath = jdbcUrl.substring("jdbc:sqlite:".length)
            File(sqliteFilePath).apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
            }
            }
        }

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            poolName = if (isSqlite) "SqlitePool" else "PgPool"
            if (jdbcUsername != null) username = jdbcUsername
            if (jdbcPassword != null) password = jdbcPassword
            // isAutoCommit = false
            minimumIdle = hikariMinIdle
            maximumPoolSize = hikariMaxConn
            if (dbIsEnableWal) connectionInitSql = "PRAGMA journal_mode= WAL;"
        }
        HikariDataSource(hikariConfig)
    }
}