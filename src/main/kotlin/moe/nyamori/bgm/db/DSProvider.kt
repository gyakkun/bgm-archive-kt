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
            // jdbcUrl = "jdbc:sqlite:" + Config.BGM_ARCHIVE_SQLITE_FILE
            jdbcUrl = "jdbc:postgresql://127.0.0.1:15432/bgm_archive"
            driverClassName = "org.postgresql.Driver"
            poolName = "PgPool"
            username = "bgm-archive"
            // isAutoCommit = false
            minimumIdle = 20
            maximumPoolSize = 50
            // if (Config.BGM_ARCHIVE_DB_IS_ENABLE_WAL) connectionInitSql = "PRAGMA journal_mode= WAL;"
        }
        HikariDataSource(hikariConfig)
    }
}