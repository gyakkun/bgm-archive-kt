package moe.nyamori.bgm.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import moe.nyamori.bgm.config.Config
import java.io.File

object DSProvider {
    val isSqlite = Config.jdbcUrl.lowercase().contains("sqlite")
    val sqliteFilePathOrNull: String?
        get() {
            if (!isSqlite) return null
            return Config.jdbcUrl.substring("jdbc:sqlite:".length)
        }
    val ds: HikariDataSource by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        if (isSqlite) {
            val sqliteFilePath = Config.jdbcUrl.substring("jdbc:sqlite:".length)
            File(sqliteFilePath).apply {
                if (!exists()) {
                    parentFile.mkdirs()
                    createNewFile()
                }
            }
        }

        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = Config.jdbcUrl
            poolName = if (isSqlite) "SqlitePool" else "PgPool"
            if (Config.jdbcUsername != null) username = Config.jdbcUsername
            if (Config.jdbcPassword != null) password = Config.jdbcPassword
            // isAutoCommit = false
            minimumIdle = Config.hikariMinIdle
            maximumPoolSize = Config.hikariMaxConn
            if (isSqlite && Config.dbIsEnableWal) connectionInitSql = "PRAGMA journal_mode= WAL;"
        }
        HikariDataSource(hikariConfig)
    }
}