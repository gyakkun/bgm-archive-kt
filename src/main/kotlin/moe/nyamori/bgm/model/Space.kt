package moe.nyamori.bgm.model

import java.util.Objects


sealed class Space(
    val type: SpaceType,
    val meta: Map<String, Any>?
)

class Subject(
    type: SpaceType = SpaceType.SUBJECT,
    meta: Map<String, Any>? = null,
    var name: String? = null,
    var displayName: String? = null,
) : Space(type, meta)

class Group(
    type: SpaceType = SpaceType.GROUP,
    meta: Map<String, Any>? = null,
    var name: String? = null,
    var displayName: String? = null,
) : Space(type, meta)


class Blog(
    type: SpaceType = SpaceType.BLOG,
    meta: Map<String, Any>? = null,
    var tags: List<String>,
    var relatedSubjectIds: List<Long>
) : Space(type, meta)

class Reserved(
    type: SpaceType
) : Space(type, null)

enum class SpaceType {
    GROUP,
    SUBJECT,
    BLOG
}