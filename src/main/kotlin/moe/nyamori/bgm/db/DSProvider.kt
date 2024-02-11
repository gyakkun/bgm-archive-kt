package moe.nyamori.bgm.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import moe.nyamori.bgm.config.Config
import java.io.File

object DSProvider {
    val ds: HikariDataSource by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
        File(Config.BGM_ARCHIVE_SQLITE_FILE).apply {
            if (!exists()) {
                parentFile.mkdirs()
                createNewFile()
            }
        }
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
            // jdbcUrl = "jdbc:sqlite:" + Config.BGM_ARCHIVE_SQLITE_FILE
            // driverClassName = "org.sqlite.JDBC"
            // poolName = "SQLitePool"
            poolName = "PgPool"
            // isAutoCommit = false
            minimumIdle = 2
            maximumPoolSize = 8
        }
        HikariDataSource(hikariConfig)
    }
}