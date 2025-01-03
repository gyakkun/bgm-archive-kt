package moe.nyamori.bgm.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import java.util.concurrent.CountDownLatch

class DaoHolder(
    private val dsProvider: DSProvider,
) {
    private val latch = CountDownLatch(1)

    fun runFlyway() {
        Flyway.configure()
            .dataSource(dsProvider.ds)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .sqlMigrationPrefix("0")
            .locations(if (dsProvider.isSqlite) "db/migration" else "db/postgres")
            .schemas(if (dsProvider.isSqlite) "main" else "public")
            .table("flyway_schema_history")
            .callbacks(object : Callback {
                override fun supports(event: Event?, context: Context?): Boolean {
                    return event!! == Event.AFTER_MIGRATE
                }

                override fun canHandleInTransaction(p0: Event?, p1: Context?): Boolean {
                    return true
                }

                override fun handle(event: Event?, context: Context?) {
                    latch.countDown()
                    System.err.println("Migration done!")
                    return
                }

                override fun getCallbackName(): String {
                    return "Hi there!"
                }
            })
            .load()
            .migrate()
    }

    private val jdbi: Jdbi = Jdbi.create(dsProvider.ds).apply {
        installPlugins()
        installPlugin(SqlObjectPlugin())
        installPlugin(KotlinPlugin())
        // setSqlLogger(Slf4JSqlLogger())
    }

    val bgmDao: IBgmDao by lazy {
        latch.await()
        val res = if (dsProvider.isSqlite) {
            jdbi.onDemand(BgmDaoSqlite::class.java)
        } else {
            jdbi.onDemand(BgmDaoPg::class.java)
        }
        return@lazy res ?: throw IllegalStateException("Should get jdbi dao class but got null")
    }

}