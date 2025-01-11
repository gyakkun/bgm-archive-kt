package moe.nyamori.bgm.config

private lateinit var delegate: IConfig

fun setConfigDelegate(iConfig: IConfig) {
    delegate = iConfig
}

object Config : IConfig by delegate