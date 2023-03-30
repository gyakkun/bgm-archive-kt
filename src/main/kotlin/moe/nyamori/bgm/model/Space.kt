package moe.nyamori.bgm.model

sealed class Space(
    val type: SpaceType
)

class Subject(
    type: SpaceType = SpaceType.SUBJECT,
    var name: String? = null,
    var displayName: String? = null,
) : Space(type)

class Group(
    type: SpaceType = SpaceType.GROUP,
    var name: String? = null,
    var displayName: String? = null,
) : Space(type)

class Blog(
    type: SpaceType = SpaceType.BLOG,
    var tags: List<String>,
    var relatedSubjectIds: List<Long>
) : Space(type)

class Reserved(
    type: SpaceType
) : Space(type)

enum class SpaceType {
    GROUP,
    SUBJECT,
    BLOG
}