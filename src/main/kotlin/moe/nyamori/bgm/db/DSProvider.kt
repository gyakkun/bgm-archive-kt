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
            jdbcUrl = "jdbc:sqlite:" + Config.BGM_ARCHIVE_SQLITE_FILE
            driverClassName = "org.sqlite.JDBC"
            poolName = "SQLitePool"
            // isAutoCommit = false
            minimumIdle = 2
            maximumPoolSize = 8
            if (Config.BGM_ARCHIVE_DB_IS_ENABLE_WAL) connectionInitSql = "PRAGMA journal_mode= WAL;"
        }
        HikariDataSource(hikariConfig)
    }
}