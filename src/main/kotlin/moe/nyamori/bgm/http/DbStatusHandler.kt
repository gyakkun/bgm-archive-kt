package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.DSProvider
import moe.nyamori.bgm.http.HumanReadable.toHumanReadableBytes
import java.io.File

object DbStatusHandler:Handler {
    override fun handle(ctx: Context) {
        ctx.prettyJson(object {
            val db = object {
                val dbFileSize = File(Config.BGM_ARCHIVE_SQLITE_FILE).length().toHumanReadableBytes()
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
                                res[rs.getString(1)] = rs.getLong(2).toHumanReadableBytes()
                            }
                            res
                        }
                    }
                }
            }
        })
    }
}