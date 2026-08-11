FastGraph is a high performance mathematical graph-theory library for JVM (written in Kotlin). If there is a choice
between read and write performance, read performance is prioritized. This library uses the term vertex (not
node or point) and edge (not arc or link).

# Topology and Data

Topology information is represented by the Graph interface and its subclasses. There is no data directly stored with the
topology. Instead, data is store in VertexProperty and EdgeProperty instances, which are created on demand through the
Graph interface (createVertexProperty/createEdgeProperty). Keeping topology and data separate brings us numerous
benefits and is one of the cornerstones of this library.

## Code Generation

This library uses code generation from templates so that it is unnecessary to maintain multiple copies of almost
identical code. The GenerateMain gradle task is responsible for taking as input the template files under
src/main/templates and generating output into src/mainGenerated - it is generally executed automatically as a
dependency of other compile tasks. Changes should thus always be made to the template files rather than the generated
output.

# Java APIs

This library is intended to be used from Java code as a first class client as well as from Kotlin. The ApiTest test
will help catch many issues with JVM APIs and Kotlin name mangling. The current ABI is found at api/fastgraph.api
and can be updated via the `updateKotlinAbi` Gradle task. This is useful for determining what is actually part of the
public API, and which names are currently mangled. This library must never ship any mangled names as part of the public
ABI.

## Value Classes

This library has several Kotlin value classes as part of its public API. Value classes are exposed to Java as the
underlying type and the Kotlin compiler will name mangle any method or property that accepts a value class as a
parameter or returns a value class.

## JvmName

In order to fix name mangling issues and keep the public API usuable from Java, the @JvmName annotation can be applied
on methods/properties. Using @JvmName on a virtual method/property may also require warning suppression (KT-31420) -
prefer to locate suppressions on the class or file rather than on the method in order to reduce spam.

## Virtual Methods/Properties

If @JvmName is applied to a virtual (override/open/abstract/interface) method or property, then it must be propagated to
all publicly visible overrides of that method/property as well. If a method/property is not renamed in this fashion then
Kotlin will generate a synthetic accessor so that it still works - but a Java client will see the mangled name of the
accessor which is why it's important to correctly rename any such methods/properties that are publicly visible to Java.

## @JvmSynthetic Methods/Properties

The @JvmSynthetic annotation is used to hide methods/properties from Java, so @JvmName should never be necessary on
something annotated with @JvmSynthetic. Generally, for any public method/property marked with @JvmSynthetic, there
should be an alternate API for a Java client to invoke the same functionality (@JvmSynthetic is often used on extension
methods which provide syntactic sugar for Kotlin clients and are not idiomatic to use from Java for example).

## Benchmarking

Local benchmarking for development should use tasks from the jmh subproject which run JVM benchmarks using JMH. It's
also usually more efficient to filter benchmarks to only the relevant classes and/or methods.

Use the "jmh" task to run all JMH benchmarks. Flags to control JMH:
* -PjmhIncludes='<regex>' to run only benchmarks which match the regular expression
* -P-i=<number> to set the number of iterations
* -P-wi=<number> to set the number of warmup iterations

Previous JMH benchmark results are stored in JSON format in a timestamped file in the jmh/benchmark-results directory.
