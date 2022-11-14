package moe.nyamori.bgm.model

data class Space( // Group or subject
    var type: SpaceType,
    var name: String? = null,
    var displayName: String? = null
    // var groupId: String? // May not get
)

enum class SpaceType {
    GROUP,
    SUBJECT
}