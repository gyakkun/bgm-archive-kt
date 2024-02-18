package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.json.JavalinJackson
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.config.Config.BGM_ARCHIVE_SQLITE_FILE
import moe.nyamori.bgm.db.DSProvider
import moe.nyamori.bgm.util.blockAndPrintProcessResults
import org.flywaydb.core.api.logging.LogFactory
import org.slf4j.LoggerFactory
import java.io.File

object AppStatusHandler : Handler {
    override fun handle(ctx: Context) {
        val res = object {
            val gitRepositories = mutableListOf<String>().apply {
                add(Config.BGM_ARCHIVE_JSON_GIT_REPO_DIR)
                add(Config.BGM_ARCHIVE_GIT_REPO_DIR)
                addAll(Config.BGM_ARCHIVE_GIT_STATIC_REPO_DIR_LIST.split(","))
                addAll(Config.BGM_ARCHIVE_JSON_GIT_STATIC_REPO_DIR_LIST.split(","))
            }
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .map { File(it) }
                .associate {
                    it.path.split(File.separator).last() to run {
                        val gitProcess = Runtime.getRuntime()
                            .exec("git count-objects -vH", null, it)
                        gitProcess.blockAndPrintProcessResults(printAtStdErr = false).map {
                            it.split(":")
                        }.associate { Pair(it[0].trim(), it[1].trim()) }
                    }
                }
            val dbFileSize = File(BGM_ARCHIVE_SQLITE_FILE).length().let { "${it / 1024 / 1024} MiB" }
            val dbTableSize = DSProvider.ds.connection.use { conn ->
                conn.prepareStatement(
                    """
                    SELECT name,
                           SUM(pgsize) as size
                    FROM "dbstat"
                    where 1 = 1
                    GROUP BY name
                    ORDER BY size desc;
                """.trimIndent()
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        val res = mutableMapOf<String, String>()
                        while (rs.next()) {
                            res[rs.getString(1)] = rs.getLong(2).let {
                                if (it > 1024 * 1024 * 1024/*GiB*/) "${it / 1024 / 1024 / 1024} GiB"
                                else if (it > 1024 * 1024/*MiB*/) "${it / 1024 / 1024} MiB"
                                else if (it > 1024/*KiB*/) "${it / 1024} KiB"
                                else "$it Bytes"
                            }
                        }
                        res
                    }
                }
            }
        }
        val jm = ctx.jsonMapper()
        if (jm is JavalinJackson) {
            ctx.json(
                jm.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(res)
                    .also { LoggerFactory.getLogger(object {}.javaClass).warn("\n" + it) })
        } else ctx.json(res)
        return
    }
}