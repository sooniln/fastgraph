package io.github.sooniln.fastgraph

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A platform-agnostic representation of type. Obtain an instance via [TypeReference.of]. Primarily used with
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
public value class TypeReference<T> private constructor(internal val kType: KType?) {

    @PublishedApi
    internal constructor(kType: KType, kClass: KClass<T & Any>) : this(kType) {
        require(kType.classifier == kClass)
    }

    @JvmName("toString")
    override fun toString(): String = kType.toString()

    public companion object {

        /**
         * A [TypeReference] for the unit type. This represents an empty type that can only ever have a single value -
         * useful if you need a meaningless type. Primarily intended for use from Java, since Kotlin code can invoke
         * reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("unit")
        public val unit: TypeReference<Unit> = of<Unit>()

        /**
         * A [TypeReference] for the primitive boolean type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("boolean")
        public val boolean: TypeReference<Boolean> = of<Boolean>()

        /**
         * A [TypeReference] for the primitive byte type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("byte")
        public val byte: TypeReference<Byte> = of<Byte>()

        /**
         * A [TypeReference] for the primitive short type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("short")
        public val short: TypeReference<Short> = of<Short>()

        /**
         * A [TypeReference] for the primitive integer type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("int")
        public val int: TypeReference<Int> = of<Int>()

        /**
         * A [TypeReference] for the primitive long type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("long")
        public val long: TypeReference<Long> = of<Long>()

        /**
         * A [TypeReference] for the primitive float type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("float")
        public val float: TypeReference<Float> = of<Float>()

        /**
         * A [TypeReference] for the primitive double type. This does not allow nulls. Primarily intended for use from
         * Java, since Kotlin code can invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @get:JvmName("double")
        public val double: TypeReference<Double> = of<Double>()

        /**
         * A [TypeReference] for any nullable object type. Primarily intended for use from Java, since Kotlin code can
         * invoke reified functions like [TypeReference.of].
         */
        @JvmStatic
        @JvmName("obj")
        public fun <T : Any> obj(): TypeReference<T> = TypeReference(null)

        /**
         * Constructs a new [TypeReference] of the given type.
         */
        @Suppress("UNCHECKED_CAST")
        @JvmSynthetic
        public inline fun <reified T> of(): TypeReference<T> {
            return TypeReference(typeOf<T>(), T::class as KClass<T & Any>)
        }
    }
}
