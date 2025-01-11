package moe.nyamori.bgm.http

import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import moe.nyamori.bgm.config.Config
import moe.nyamori.bgm.db.Dao
import moe.nyamori.bgm.util.HttpHelper
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object DbPurgeAllMetaHandler : Handler {
    val LOGGER = LoggerFactory.getLogger(DbPurgeAllMetaHandler.javaClass)
    private val _random = Random()

    @Volatile
    private var _captcha = _random.nextInt(8192)

    @Volatile
    private var _waiting = false

    @Volatile
    private var _captchaTimestamp = -1L
    private const val _captchaTimeout = 60_000 // ms
    override fun handle(ctx: Context) {
        if (System.currentTimeMillis() - _captchaTimestamp > _captchaTimeout) _waiting = false
        val keyParam = ctx.queryParam("key")
        val v = ctx.queryParam("v")
        if (Config.BGM_ARCHIVE_DISABLE_DB_PERSIST || keyParam != Config.BGM_ARCHIVE_DB_PERSIST_KEY) {
            ctx.status(HttpStatus.BAD_REQUEST)
            return
        }
        LOGGER.error(
            """
            
            ############## !!!!!!! WARNING !!!!!!! #############
            !!!!!!!!!!!!!! YOU ARE PURGING ALL META !!!!!!!!!!!!
            ############## !!!!!!! ^^^^^^^ !!!!!!! #############
        """.trimIndent()
        )
        if (!_waiting) {
            _captcha = _random.nextInt(1000)
            _captchaTimestamp = System.currentTimeMillis()
            _waiting = true
            ctx.result(
                """
                
                ############## !!!!!!! WARNING !!!!!!! #############
                !!!!!!!!!!!!!! YOU ARE PURGING ALL META !!!!!!!!!!!!
                !!!!!!!!!!!!!! PLEASE INPUT THE FLOWING !!!!!!!!!!!!
                !!!!!!!!!!!!!! CAPTCHA AS PARAM "v"     !!!!!!!!!!!!
                !!!!!!!!!!!!!!      $_captcha                !!!!!!!!!!!!
                !!!!!!!!!!!!!! \EOF                     !!!!!!!!!!!!
                ############## !!!!!!! ^^^^^^^ !!!!!!! #############
            """.trimIndent()
            )
            ctx.status(HttpStatus.OK)
            return
        } else {
            val vInt = v?.toIntOrNull()
            if (vInt == null || vInt != _captcha) {
                ctx.status(HttpStatus.BAD_REQUEST)
                ctx.result("########## WRONG CAPTCHA! ###########")
                _waiting = false
                return
            }
        }


        try {
            if (HttpHelper.tryLockDbMs(10_000)) {
                val result = Dao.bgmDao._TRUNCATE_ALL_META()
                LOGGER.error("Truncate all meta. Lines deleted: $result")
                ctx.result(
                    """
                    ############## !!!!!!! WARNING !!!!!!! #############
                    !!!!!!!!!!!!!! RESULT OF PURGE:         !!!!!!!!!!!!
                    !!!!!!!!!!!!!! $result                        !!!!!!!!!!!!
                    !!!!!!!!!!!!!! \EOF                     !!!!!!!!!!!!
                    ############## !!!!!!! ^^^^^^^ !!!!!!! #############
                """.trimIndent()
                )
                _waiting = false
            } else {
                throw TimeoutException("Timeout when trying to lock db!")
            }
            ctx.status(HttpStatus.OK)
        } catch (ex: Exception) {
            LOGGER.error("Error when purging meta: ", ex)
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR)
            ctx.result(ex.message ?: "Some error. Please check log.")
        } finally {
            HttpHelper.tryUnlockDb()
        }

    }
}