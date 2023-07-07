package moe.nyamori.bgm.model


sealed class Space(
    val type: SpaceType,
    val meta: Map<String, Any>?,
    val name: String?,
    val displayName: String?
)

class Subject(
    type: SpaceType = SpaceType.SUBJECT,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(type, meta, name, displayName)

class Group(
    type: SpaceType = SpaceType.GROUP,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(type, meta, name, displayName)


class Blog(
    type: SpaceType = SpaceType.BLOG,
    meta: Map<String, Any>? = null,
    var tags: List<String>? = null,
    var relatedSubjectIds: List<Int>? = null
) : Space(type, meta, null, null)


class Ep(
    type: SpaceType = SpaceType.EP,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(type, meta, name, displayName)

class Character(
    type: SpaceType = SpaceType.CHARACTER,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(type, meta, name, displayName)

class Person(
    type: SpaceType = SpaceType.PERSON,
    meta: Map<String, Any>? = null,
    name: String? = null,
    displayName: String? = null
) : Space(type, meta, name, displayName)

class Reserved(
    type: SpaceType
) : Space(type, null, null, null)

enum class SpaceType(val id: Int) {
    GROUP(8),
    SUBJECT(10),
    EP(11),
    BLOG(100), // TBC
    PERSON(200), // TBC
    CHARACTER(300) // TBC
}