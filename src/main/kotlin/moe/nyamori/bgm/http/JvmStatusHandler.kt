package moe.nyamori.bgm.http

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import io.javalin.http.Context
import io.javalin.http.Handler
import io.javalin.json.JavalinJackson
import io.javalin.json.toJsonString
import moe.nyamori.bgm.http.HumanReadable.commaFormatted
import moe.nyamori.bgm.http.HumanReadable.toHumanReadableBytes
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.sql.Timestamp
import java.time.Instant
import kotlin.math.abs


object JvmStatusHandler : Handler {
    private val LOGGER = LoggerFactory.getLogger(JvmStatusHandler::class.java)
    private const val STRING_THRESHOLD = 100
    private val USER_HOME = System.getProperty("user.home")
    private val USER_NAME = System.getProperty("user.name")
    private val HOSTNAME = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("localhost")
    private val TO_MASKED_FIELDS = setOf(USER_NAME, USER_HOME, HOSTNAME)
    private val customJackson = JavalinJackson().updateMapper {
        it.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        it.enable(SerializationFeature.INDENT_OUTPUT)
        it.registerModules(SimpleModule().apply {
            addSerializer(String::class.java, object : JsonSerializer<String>() {
                override fun serialize(value: String, gen: JsonGenerator, serializers: SerializerProvider) {
                    if (TO_MASKED_FIELDS.any { value.contains(it) }) gen.writeString("(**MASKED**)")
                    else if (value.length > STRING_THRESHOLD) gen.writeString("(**Super long field**)")
                    else gen.writeString(value)
                }
            })
            setSerializerModifier(object : BeanSerializerModifier() {
                val millisFields =
                    setOf(
                        "currentThreadCpuTime",
                        "currentThreadUserTime",
                        "uptime",
                        "duration",
                        "collectionTime",
                        "totalCompilationTime"
                    )
                val nanosFields = setOf("processCpuTime")
                val timestampFields = setOf("startTime", "endTime")
                val plainFields = setOf(
                    "pid",
                    "totalStartedThreadCount",
                    "collectionCount",
                    "id",
                    "totalLoadedClassCount",
                    "unloadedClassCount"
                )
                val skipFields =
                    setOf("objectPendingFinalizationCount", "objectName", "allThreadIds", "notificationInfo")

                override fun changeProperties(
                    config: SerializationConfig,
                    beanDesc: BeanDescription,
                    beanProperties: List<BeanPropertyWriter>
                ): List<BeanPropertyWriter> = beanProperties.map { bpw: BeanPropertyWriter ->
                    object : BeanPropertyWriter(bpw) {
                        @Throws(Exception::class)
                        override fun serializeAsField(bean: Any, gen: JsonGenerator, prov: SerializerProvider) =
                            runCatching {
                                val k = this.name
                                if (Long::class.java.isAssignableFrom(this.type.rawClass)) {
                                    val v = this.get(bean) as Long /* value*/
                                    when (k) {
                                        in nanosFields -> gen.writeStringField(k, "${v.commaFormatted()} ns")
                                        in millisFields -> gen.writeStringField(k, "${v.commaFormatted()} ms")
                                        in timestampFields -> {
                                            if (abs(v - System.currentTimeMillis()) <= 365L * 86400 * 1000) {
                                                gen.writeStringField(k, Timestamp(v).toInstant().toString())
                                            } else {
                                                val actualTs = ManagementFactory.getRuntimeMXBean().startTime + v
                                                val diffNow = System.currentTimeMillis() - actualTs
                                                gen.writeStringField(
                                                    k,
                                                    "T-${diffNow.commaFormatted()} ms , T+${v.commaFormatted()} ms - "
                                                            + Timestamp(actualTs).toInstant().toString()
                                                )
                                            }
                                        }

                                        in plainFields -> gen.writeNumberField(k, v)
                                        else -> {
                                            // System.err.println(k)
                                            gen.writeStringField(k, v.toHumanReadableBytes(commaEvery3Digit = true))
                                        }
                                    }
                                    return@runCatching
                                }
                                if (k in skipFields) return@runCatching
                                super.serializeAsField(bean, gen, prov)
                            }.onFailure {
                                LOGGER.debug(
                                    "ignoring ${it.javaClass.name} for field ${this.name} of ${bean.javaClass.name} instance."
                                )
                            }.getOrDefault(Unit)
                    }
                }
            })
        })
    }

    override fun handle(ctx: Context) {
        val res = object {
            val memory = ManagementFactory.getMemoryMXBean()
            val thread = ManagementFactory.getThreadMXBean()
            val runtime = ManagementFactory.getRuntimeMXBean()
            val compilation = ManagementFactory.getCompilationMXBean()
            val memoryManagers = ManagementFactory.getMemoryManagerMXBeans()
            val memoryPools = ManagementFactory.getMemoryPoolMXBeans()
            val classLoading = ManagementFactory.getClassLoadingMXBean()
            val os = ManagementFactory.getOperatingSystemMXBean()
            val gc = ManagementFactory.getGarbageCollectorMXBeans()
        }
        ctx.json(customJackson.toJsonString(res))
    }
}