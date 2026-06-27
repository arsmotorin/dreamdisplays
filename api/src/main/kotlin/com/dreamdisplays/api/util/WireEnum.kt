package com.dreamdisplays.api.util

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/** Enum-like value with a stable wire token. */
@DreamDisplaysUnstableApi
interface WireEnum {
    val wire: String
}

/** Finds a wire enum value by token, returning [default] when [raw] is blank or unknown. */
@DreamDisplaysUnstableApi
inline fun <reified E> wireEnumValueOf(raw: String?, default: E): E
        where E : Enum<E>, E : WireEnum =
    wireEnumValueOfOrNull<E>(raw) ?: default

/** Finds a wire enum value by token, or `null` when [raw] is blank or unknown. */
@DreamDisplaysUnstableApi
inline fun <reified E> wireEnumValueOfOrNull(raw: String?): E?
        where E : Enum<E>, E : WireEnum {
    val token = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return enumValues<E>().firstOrNull { it.wire == token }
}
