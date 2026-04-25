package moe.nyamori.bgm.config

import java.lang.reflect.Proxy

private lateinit var delegate: IConfig

fun setConfigDelegate(iConfig: IConfig) {
    delegate = iConfig
}

//val Config: IConfig
//    get() = delegate

object Config : IConfig by (Proxy.newProxyInstance(
    IConfig::class.java.classLoader,
    arrayOf(IConfig::class.java),
    java.lang.reflect.InvocationHandler { _, method, args ->
        if (args == null) {
            method.invoke(delegate)
        } else {
            method.invoke(delegate, *args)
        }
    }
) as IConfig)