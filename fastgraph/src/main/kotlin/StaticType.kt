/**
 * Methods dealing with [StaticType].
 */
@file:JvmName("StaticTypes")

package io.github.sooniln.fastgraph

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Constructs a new [StaticType] of the given type.
 */
@Suppress("UNCHECKED_CAST")
@JvmSynthetic
public inline fun <reified T> staticTypeOf(): StaticType<T> {
    return StaticType(typeOf<T>(), T::class as KClass<T & Any>)
}

/**
 * A platform-agnostic representation of type. Obtain an instance via [staticTypeOf]. Primarily used with
 * [VertexProperty] and [EdgeProperty], so that a property can know what type it is storing, and behave appropriately.
 * Kotlin clients should rarely if ever need to interact with this class, since all reified overloads handle this
 * automatically. Since Java clients cannot invoke reified methods, they may need to use this class to pass in type
 * information to [Graph.createVertexProperty] and [Graph.createEdgeProperty] directly.
 *
 * Java clients can use [unit], [boolean], [byte], [short], [int], [long], [float], or [double] to indicate the type of
 * primitive property they want. If Java clients want a normal Object property (which allows null values), use [obj].
 * Prefer never to use [obj] from Kotlin - not only is it unnecessary, but it loses information as the property is no
 * longer aware of what type it is storing.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
@OptIn(ExperimentalStdlibApi::class)
@JvmInline
@JvmExposeBoxed
public value class StaticType<T> private constructor(public val kType: KType?) {

    /** Prefer to use [staticTypeOf] instead (or for Java client, use the static class methods). */
    public constructor(kType: KType, kClass: KClass<T & Any>) : this(kType) {
        require(kType.classifier == kClass)
    }

    @JvmName("mayCastTo")
    public fun <O> mayCastTo(other: StaticType<O>): Boolean {
        return other.kType != null && mayCastTo(other.kType)
    }

    @JvmSynthetic
    public fun mayCastTo(other: KType): Boolean {
        return kType?.classifier != null
            && other.classifier != null
            && kType.classifier == other.classifier
            && (other.isMarkedNullable || kType.isMarkedNullable == other.isMarkedNullable)
    }

    override fun toString(): String = kType.toString()

    public companion object {

        /**
         * A [StaticType] for the unit type. This represents an empty type that can only ever have a single value -
         * useful if you need a meaningless type. Primarily intended for use from Java, since Kotlin code can invoke
         * reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("unit")
        public val unit: StaticType<Unit> = staticTypeOf<Unit>()

        /**
         * A [StaticType] for the primitive boolean type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("boolean")
        public val boolean: StaticType<Boolean> = staticTypeOf<Boolean>()

        /**
         * A [StaticType] for the primitive byte type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("byte")
        public val byte: StaticType<Byte> = staticTypeOf<Byte>()

        /**
         * A [StaticType] for the primitive short type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("short")
        public val short: StaticType<Short> = staticTypeOf<Short>()

        /**
         * A [StaticType] for the primitive integer type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("int")
        public val int: StaticType<Int> = staticTypeOf<Int>()

        /**
         * A [StaticType] for the primitive long type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("long")
        public val long: StaticType<Long> = staticTypeOf<Long>()

        /**
         * A [StaticType] for the primitive float type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("float")
        public val float: StaticType<Float> = staticTypeOf<Float>()

        /**
         * A [StaticType] for the primitive double type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [staticTypeOf].
         */
        @JvmStatic
        @get:JvmExposeBoxed
        @get:JvmName("double")
        public val double: StaticType<Double> = staticTypeOf<Double>()

        /**
         * A [StaticType] for any nullable object type. Primarily intended for use from Java, since Kotlin code can
         * invoke reified functions like [staticTypeOf]. Do not use from Kotlin, as this loses type information.
         */
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @JvmExposeBoxed
        @JvmName("obj")
        public fun <T> obj(): StaticType<T> = nullable as StaticType<T>

        private val nullable = StaticType<Nothing>(null)
    }
}
