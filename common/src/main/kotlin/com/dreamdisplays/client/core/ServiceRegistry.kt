package com.dreamdisplays.client.core

interface ServiceRegistry {
    fun <T : Any> register(type: Class<T>, instance: T)
    fun <T : Any> get(type: Class<T>): T
    fun <T : Any> getOrNull(type: Class<T>): T?
    fun <T : Any> has(type: Class<T>): Boolean
}

inline fun <reified T : Any> ServiceRegistry.register(instance: T): Unit = register(T::class.java, instance)
inline fun <reified T : Any> ServiceRegistry.get(): T = get(T::class.java)
inline fun <reified T : Any> ServiceRegistry.getOrNull(): T? = getOrNull(T::class.java)
inline fun <reified T : Any> ServiceRegistry.has(): Boolean = has(T::class.java)
