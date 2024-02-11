package moe.nyamori.bgm.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.callback.Callback
import org.flywaydb.core.api.callback.Context
import org.flywaydb.core.api.callback.Event
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.sqlobject.SqlObjectPlugin
import java.util.concurrent.CountDownLatch

object Dao {
    private val latch = CountDownLatch(1)

    init {
        Flyway.configure()
            .dataSource(DSProvider.ds)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .sqlMigrationPrefix("0")
            .locations("db/migration")
            .schemas("main")
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

    private val jdbi: Jdbi = Jdbi.create(DSProvider.ds).apply {
        installPlugin(SqlObjectPlugin())
        installPlugin(KotlinPlugin())
        // setSqlLogger(Slf4JSqlLogger())
    }

    fun bgmDao(): BgmDao {
        latch.await()
        return jdbi.onDemand(BgmDao::class.java)
            ?: throw IllegalStateException("Should get jdbi dao class but got null")
    }

}