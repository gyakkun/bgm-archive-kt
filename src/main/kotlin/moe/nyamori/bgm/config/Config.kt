package moe.nyamori.bgm.config

private lateinit var delegate: IConfig

object Config : IConfig by delegate {
    fun setDelegate(iConfig: IConfig) {
        delegate = iConfig
    }
}