package dev.arc.server.nms

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

/**
 * Concurrent, negative-caching reflection toolkit for the mojang-mapped
 * runtime. Compatible overloads are selected by argument type rather than by
 * arity alone, avoiding mapping-dependent "first method wins" behaviour.
 */
internal object Reflect {

    private data class ExactMethodKey(
        val owner: Class<*>,
        val name: String,
        val params: List<Class<*>>,
    )

    private data class InvocationKey(
        val owner: Class<*>,
        val name: String,
        val args: List<Class<*>?>,
        val staticOnly: Boolean,
    )

    private data class FieldKey(val owner: Class<*>, val name: String)
    private data class ConstructorKey(val owner: Class<*>, val params: List<Class<*>>)
    private data class ArityConstructorKey(val owner: Class<*>, val args: List<Class<*>?>)

    private val classes = ConcurrentHashMap<String, Optional<Class<*>>>()
    private val exactMethods = ConcurrentHashMap<ExactMethodKey, Optional<Method>>()
    private val invocationMethods = ConcurrentHashMap<InvocationKey, Optional<Method>>()
    private val fields = ConcurrentHashMap<FieldKey, Optional<Field>>()
    private val constructors = ConcurrentHashMap<ConstructorKey, Optional<Constructor<*>>>()
    private val compatibleConstructors = ConcurrentHashMap<ArityConstructorKey, Optional<Constructor<*>>>()
    private val methodHandles = ConcurrentHashMap<Method, Optional<MethodHandle>>()
    private val constructorHandles = ConcurrentHashMap<Constructor<*>, Optional<MethodHandle>>()
    private val fieldGetterHandles = ConcurrentHashMap<Field, Optional<MethodHandle>>()
    private val hits = LongAdder()
    private val misses = LongAdder()
    private val resolutionFailures = LongAdder()
    private val invocationFailures = LongAdder()

    fun cls(name: String): Class<*>? = cached(classes, name) {
        runCatching { Class.forName(name) }.getOrNull()
    }

    fun handleOf(obj: Any): Any? {
        val method = method(obj.javaClass, "getHandle") ?: return null
        return invoke(method, obj)
    }

    fun method(type: Class<*>, name: String, vararg params: Class<*>): Method? =
        cached(exactMethods, ExactMethodKey(type, name, params.toList())) {
            runCatching { type.getMethod(name, *params) }.getOrNull()
                ?: hierarchy(type)
                    .mapNotNull { owner -> runCatching { owner.getDeclaredMethod(name, *params) }.getOrNull() }
                    .firstOrNull()
        }?.accessible()

    /**
     * Retained for sites that only know the signature arity. Resolution is
     * deterministic, but new invocation code should use [invoke].
     */
    fun methodByArity(type: Class<*>, name: String, arity: Int): Method? =
        allMethods(type)
            .filter { it.name == name && it.parameterCount == arity }
            .sortedWith(compareBy<Method>({ it.isBridge }, { it.isSynthetic }, { it.toGenericString() }))
            .firstOrNull()
            ?.accessible()

    fun field(type: Class<*>, name: String): Field? =
        cached(fields, FieldKey(type, name)) {
            hierarchy(type)
                .mapNotNull { owner -> runCatching { owner.getDeclaredField(name) }.getOrNull() }
                .firstOrNull()
        }?.accessible()

    fun fieldValue(field: Field, target: Any?): Any? {
        val handle = fieldGetterHandle(field)
        if (handle != null) {
            val result = runCatching {
                if (Modifier.isStatic(field.modifiers)) {
                    handle.invokeWithArguments()
                } else {
                    handle.invokeWithArguments(target)
                }
            }
            if (result.isSuccess) return result.getOrNull()
        }
        return attempted { field.get(target) }
    }

    fun intField(field: Field, target: Any?): Int? =
        (fieldValue(field, target) as? Number)?.toInt()

    fun invoke(target: Any, name: String, vararg args: Any?): Any? {
        val method = compatibleMethod(target.javaClass, name, args, staticOnly = false) ?: return null
        return invoke(method, target, *args)
    }

    fun invokeStatic(type: Class<*>, name: String, vararg args: Any?): Any? {
        val method = compatibleMethod(type, name, args, staticOnly = true) ?: return null
        return invoke(method, null, *args)
    }

    fun invoke(method: Method, target: Any?, vararg args: Any?): Any? {
        val handle = methodHandle(method)
        if (handle != null) {
            val arguments = if (Modifier.isStatic(method.modifiers)) {
                args.asList()
            } else {
                listOf(target) + args
            }
            val result = runCatching { handle.invokeWithArguments(arguments) }
            if (result.isSuccess) return result.getOrNull()
        }
        return attempted { method.invoke(target, *args) }
    }

    fun construct(type: Class<*>, params: Array<Class<*>>, vararg args: Any?): Any? {
        val constructor = cached(constructors, ConstructorKey(type, params.toList())) {
            runCatching { type.getConstructor(*params) }.getOrNull()
                ?: runCatching { type.getDeclaredConstructor(*params) }.getOrNull()
        }?.accessible() ?: return null
        return invokeConstructor(constructor, args)
    }

    fun constructByArity(type: Class<*>, arity: Int, vararg args: Any?): Any? {
        if (arity != args.size) return null
        val key = ArityConstructorKey(type, argumentTypes(args))
        val constructor = cached(compatibleConstructors, key) {
            type.declaredConstructors
                .filter { it.parameterCount == arity }
                .mapNotNull { candidate ->
                    compatibilityScore(candidate.parameterTypes, args)?.let { candidate to it }
                }
                .sortedWith(compareByDescending<Pair<Constructor<*>, Int>> { it.second }
                    .thenBy { it.first.toGenericString() })
                .firstOrNull()
                ?.first
        }?.accessible() ?: return null
        return invokeConstructor(constructor, args)
    }

    fun stats(): ReflectionCacheStats = ReflectionCacheStats(
        classes = classes.size,
        methods = exactMethods.size + invocationMethods.size,
        fields = fields.size,
        constructors = constructors.size + compatibleConstructors.size,
        methodHandles = methodHandles.size + constructorHandles.size + fieldGetterHandles.size,
        hits = hits.sum(),
        misses = misses.sum(),
        resolutionFailures = resolutionFailures.sum(),
        invocationFailures = invocationFailures.sum(),
    )

    fun clearCaches() {
        classes.clear()
        exactMethods.clear()
        invocationMethods.clear()
        fields.clear()
        constructors.clear()
        compatibleConstructors.clear()
        methodHandles.clear()
        constructorHandles.clear()
        fieldGetterHandles.clear()
        hits.reset()
        misses.reset()
        resolutionFailures.reset()
        invocationFailures.reset()
    }

    private fun compatibleMethod(
        type: Class<*>,
        name: String,
        args: Array<out Any?>,
        staticOnly: Boolean,
    ): Method? {
        val key = InvocationKey(type, name, argumentTypes(args), staticOnly)
        return cached(invocationMethods, key) {
            allMethods(type)
                .filter { candidate ->
                    candidate.name == name &&
                        candidate.parameterCount == args.size &&
                        (!staticOnly || Modifier.isStatic(candidate.modifiers))
                }
                .mapNotNull { candidate ->
                    compatibilityScore(candidate.parameterTypes, args)?.let { candidate to it }
                }
                .sortedWith(compareByDescending<Pair<Method, Int>> { it.second }
                    .thenBy { it.first.isBridge }
                    .thenBy { it.first.isSynthetic }
                    .thenBy { it.first.toGenericString() })
                .firstOrNull()
                ?.first
        }?.accessible()
    }

    private fun compatibilityScore(params: Array<Class<*>>, args: Array<out Any?>): Int? {
        var score = 0
        for (index in params.indices) {
            val parameter = params[index]
            val argument = args[index]
            if (argument == null) {
                if (parameter.isPrimitive) return null
                score += 1
                continue
            }
            val argumentType = argument.javaClass
            when {
                boxed(parameter) == boxed(argumentType) -> score += 16
                boxed(parameter).isAssignableFrom(boxed(argumentType)) -> {
                    score += 8 - typeDistance(boxed(argumentType), boxed(parameter)).coerceAtMost(7)
                }
                else -> return null
            }
        }
        return score
    }

    private fun allMethods(type: Class<*>): Sequence<Method> =
        (
            hierarchy(type).flatMap { owner -> owner.declaredMethods.asSequence() } +
                type.methods.asSequence()
            ).distinctBy(Method::toGenericString)

    private fun hierarchy(type: Class<*>): Sequence<Class<*>> =
        generateSequence(type) { it.superclass }

    private fun argumentTypes(args: Array<out Any?>): List<Class<*>?> =
        args.map { it?.javaClass }

    private fun typeDistance(child: Class<*>, parent: Class<*>): Int {
        if (child == parent) return 0
        val visited = HashSet<Class<*>>()
        var frontier = listOf(child)
        var distance = 0
        while (frontier.isNotEmpty()) {
            distance++
            val next = ArrayList<Class<*>>()
            for (type in frontier) {
                val candidates = buildList {
                    type.superclass?.let(::add)
                    addAll(type.interfaces)
                }
                for (candidate in candidates) {
                    if (candidate == parent) return distance
                    if (visited.add(candidate)) next += candidate
                }
            }
            frontier = next
        }
        return Int.MAX_VALUE
    }

    private fun methodHandle(method: Method): MethodHandle? =
        cached(methodHandles, method) {
            method.trySetAccessible()
            runCatching { MethodHandles.lookup().unreflect(method) }.getOrNull()
        }

    private fun constructorHandle(constructor: Constructor<*>): MethodHandle? =
        cached(constructorHandles, constructor) {
            constructor.trySetAccessible()
            runCatching { MethodHandles.lookup().unreflectConstructor(constructor) }.getOrNull()
        }

    private fun fieldGetterHandle(field: Field): MethodHandle? =
        cached(fieldGetterHandles, field) {
            field.trySetAccessible()
            runCatching { MethodHandles.lookup().unreflectGetter(field) }.getOrNull()
        }

    private fun invokeConstructor(constructor: Constructor<*>, args: Array<out Any?>): Any? {
        val handle = constructorHandle(constructor)
        return if (handle != null) {
            val result = runCatching { handle.invokeWithArguments(args.asList()) }
            if (result.isSuccess) result.getOrNull() else attempted { constructor.newInstance(*args) }
        } else {
            attempted { constructor.newInstance(*args) }
        }
    }

    private fun boxed(type: Class<*>): Class<*> = when (type) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        Integer.TYPE -> Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        else -> type
    }

    private fun <K, V : Any> cached(
        cache: ConcurrentHashMap<K, Optional<V>>,
        key: K,
        resolver: () -> V?,
    ): V? {
        val existing = cache[key]
        if (existing != null) {
            hits.increment()
            return existing.orElse(null)
        }
        misses.increment()
        return cache.computeIfAbsent(key) {
            val resolved = resolver()
            if (resolved == null) resolutionFailures.increment()
            Optional.ofNullable(resolved)
        }.orElse(null)
    }

    private inline fun <T> attempted(block: () -> T): T? =
        runCatching(block).getOrElse {
            invocationFailures.increment()
            null
        }

    private fun Method.accessible(): Method = apply { trySetAccessible() }
    private fun Field.accessible(): Field = apply { trySetAccessible() }
    private fun Constructor<*>.accessible(): Constructor<*> = apply { trySetAccessible() }
}

internal data class ReflectionCacheStats(
    val classes: Int,
    val methods: Int,
    val fields: Int,
    val constructors: Int,
    val methodHandles: Int,
    val hits: Long,
    val misses: Long,
    val resolutionFailures: Long,
    val invocationFailures: Long,
)
