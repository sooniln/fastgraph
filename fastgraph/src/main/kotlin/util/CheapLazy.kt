package io.github.sooniln.fastgraph.util

internal inline fun <T : Any> cheapLazy(crossinline initializer: () -> T): Lazy<T> = object : Lazy<T> {
    private var _value: T? = null

    override val value: T
        get() {
            var v = _value
            if (v == null) {
                v = initializer()
                _value = v
            }
            return v
        }

    override fun isInitialized(): Boolean = _value != null
    override fun toString(): String = _value?.toString() ?: "<uninitialized>"
}
