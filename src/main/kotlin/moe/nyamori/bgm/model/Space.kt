package moe.nyamori.bgm.model


sealed class Space(
    val name: String?,
    val displayName: String?,
    val type: SpaceType,
    val meta: Map<String, Any>?,
)

class Subject(
    type: SpaceType = SpaceType.SUBJECT,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(name, displayName, type, meta)

class Group(
    type: SpaceType = SpaceType.GROUP,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(name, displayName, type, meta)


class Blog(
    type: SpaceType = SpaceType.BLOG,
    meta: Map<String, Any>? = null,
    var tags: List<String>? = null,
    var relatedSubjectIds: List<Int>? = null
) : Space(null, null, type, meta)


class Ep(
    type: SpaceType = SpaceType.EP,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(name, displayName, type, meta)

class Character(
    type: SpaceType = SpaceType.CHARACTER,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(name, displayName, type, meta)

class Person(
    type: SpaceType = SpaceType.PERSON,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(name, displayName, type, meta)

class Reserved(
    type: SpaceType
) : Space(null, null, type, null)

enum class SpaceType(val id: Int) {
    GROUP(8),
    SUBJECT(10),
    EP(11),
    BLOG(100), // TBC
    PERSON(200), // TBC
    CHARACTER(300) // TBC
}