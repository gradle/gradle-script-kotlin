/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.kotlin.dsl.codegen

import org.gradle.api.Incubating

import org.gradle.kotlin.dsl.accessors.primitiveTypeStrings
import org.gradle.kotlin.dsl.support.ClassBytesRepository
import org.gradle.kotlin.dsl.support.classPathBytesRepositoryFor

import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.Attribute
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_CODE
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.jetbrains.org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.jetbrains.org.objectweb.asm.Opcodes.ACC_STATIC
import org.jetbrains.org.objectweb.asm.Opcodes.ASM6
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.TypePath
import org.jetbrains.org.objectweb.asm.signature.SignatureReader
import org.jetbrains.org.objectweb.asm.signature.SignatureVisitor
import org.jetbrains.org.objectweb.asm.tree.AnnotationNode
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode

import java.io.Closeable
import java.io.File

import javax.annotation.Nullable

import kotlin.LazyThreadSafetyMode.NONE


internal
fun apiTypeProviderFor(
    jarsOrDirs: List<File>,
    parameterNamesSupplier: ParameterNamesSupplier = { null }
): ApiTypeProvider =

    ApiTypeProvider(classPathBytesRepositoryFor(jarsOrDirs), parameterNamesSupplier)


private
typealias ApiTypeSupplier = () -> ApiType


internal
typealias ParameterNamesSupplier = (String) -> List<String>?


/**
 * Provides [ApiType] instances by Kotlin source name from a class path.
 *
 * Keeps JAR files open for fast lookup, must be closed.
 * Once closed, type graph navigation from [ApiType] and [ApiFunction] instances
 * will throw [IllegalStateException].
 *
 * Limitations:
 * - supports Java byte code only, not Kotlin
 * - does not support nested Java arrays as method parameters
 * - does not distinguish co-variance and contra-variance
 */
internal
class ApiTypeProvider(
    private val repository: ClassBytesRepository,
    parameterNamesSupplier: ParameterNamesSupplier
) : Closeable {

    private
    val context = Context(this, parameterNamesSupplier)

    private
    val apiTypesBySourceName = mutableMapOf<String, ApiTypeSupplier?>()

    private
    var closed = false

    fun type(sourceName: String): ApiType? = open {
        apiTypesBySourceName.computeIfAbsent(sourceName) {
            repository.classBytesFor(sourceName)?.let { apiTypeFor(sourceName, { it }) }
        }?.invoke()
    }

    fun allTypes(): Sequence<ApiType> = open {
        repository.allClassesBytesBySourceName().map { (sourceName, classBytes) ->
            apiTypesBySourceName.computeIfAbsent(sourceName) {
                apiTypeFor(sourceName, classBytes)
            }!!
        }.map { it() }
    }

    override fun close() =
        try {
            repository.close()
        } finally {
            closed = true
        }

    private
    fun apiTypeFor(sourceName: String, classBytes: () -> ByteArray) = {
        ApiType(sourceName, classNodeFor(classBytes), context)
    }

    private
    fun classNodeFor(classBytesSupplier: () -> ByteArray) = {
        ApiTypeClassNode().also {
            ClassReader(classBytesSupplier()).accept(it, SKIP_DEBUG or SKIP_CODE or SKIP_FRAMES)
        }
    }

    private
    fun <T> open(action: () -> T): T =
        if (closed) throw IllegalStateException("ApiTypeProvider closed!")
        else action()

    internal
    class Context(
        private val typeProvider: ApiTypeProvider,
        private val parameterNamesSupplier: ParameterNamesSupplier
    ) {
        fun type(sourceName: String): ApiType? =
            typeProvider.type(sourceName)

        fun parameterNamesFor(typeName: String, functionName: String, parameterTypeNames: List<String>): List<String>? =
            parameterNamesSupplier("$typeName.$functionName(${parameterTypeNames.joinToString(",")})")
    }
}


internal
class ApiType(
    val sourceName: String,
    private val delegateSupplier: () -> ClassNode,
    private val context: ApiTypeProvider.Context
) {

    val isPublic: Boolean
        get() = (ACC_PUBLIC and delegate.access) > 0

    val isDeprecated: Boolean
        get() = delegate.visibleAnnotations.has<java.lang.Deprecated>()

    val isIncubating: Boolean
        get() = delegate.visibleAnnotations.has<Incubating>()

    val typeParameters: List<ApiTypeUsage> by lazy(NONE) {
        context.apiTypeParametersFor(visitedSignature)
    }

    val functions: List<ApiFunction> by lazy(NONE) {
        delegate.methods.filter { it.signature != null }.map { ApiFunction(this, it, context) }
    }

    private
    val delegate: ClassNode by lazy(NONE) {
        delegateSupplier()
    }

    private
    val visitedSignature: ClassSignatureVisitor? by lazy(NONE) {
        delegate.signature?.let { signature ->
            ClassSignatureVisitor().also { SignatureReader(signature).accept(it) }
        }
    }
}


internal
class ApiFunction(
    val owner: ApiType,
    private val delegate: MethodNode,
    private val context: ApiTypeProvider.Context
) {

    val name: String =
        delegate.name

    val isPublic: Boolean =
        (ACC_PUBLIC and delegate.access) > 0

    val isDeprecated: Boolean
        get() = owner.isDeprecated || delegate.visibleAnnotations.has<java.lang.Deprecated>()

    val isIncubating: Boolean
        get() = owner.isIncubating || delegate.visibleAnnotations.has<Incubating>()

    val isStatic: Boolean =
        (ACC_STATIC and delegate.access) > 0

    val typeParameters: List<ApiTypeUsage> by lazy(NONE) {
        context.apiTypeParametersFor(visitedSignature)
    }

    val parameters: List<ApiFunctionParameter> by lazy(NONE) {
        context.apiFunctionParametersFor(this, delegate, visitedSignature)
    }

    val returnType: ApiTypeUsage by lazy(NONE) {
        context.apiTypeUsageForReturnType(delegate, visitedSignature.returnType)
    }

    private
    val visitedSignature: MethodSignatureVisitor by lazy(NONE) {
        MethodSignatureVisitor().also { visitor -> SignatureReader(delegate.signature).accept(visitor) }
    }
}


internal
data class ApiTypeUsage(
    val sourceName: String,
    val isNullable: Boolean = false,
    val type: ApiType? = null,
    val typeArguments: List<ApiTypeUsage> = emptyList(),
    val bounds: List<ApiTypeUsage> = emptyList()
) {
    val isRaw: Boolean
        get() = typeArguments.isEmpty()
            && type?.typeParameters?.isEmpty() != false
}


internal
data class ApiFunctionParameter(
    val index: Int,
    private val nameSupplier: () -> String?,
    val type: ApiTypeUsage
) {

    val name: String? by lazy(NONE) {
        nameSupplier()
    }
}


private
fun ApiTypeProvider.Context.apiTypeUsageFor(
    binaryName: String,
    isNullable: Boolean = false,
    typeArguments: List<TypeSignatureVisitor> = emptyList(),
    bounds: List<TypeSignatureVisitor> = emptyList()
): ApiTypeUsage =

    if (binaryName == "?") starProjectionTypeUsage
    else sourceNameOfBinaryName(binaryName).let { sourceName ->
        ApiTypeUsage(
            sourceName,
            isNullable,
            type(sourceName),
            typeArguments.map { apiTypeUsageFor(it.binaryName, typeArguments = it.typeArguments) },
            bounds.map { apiTypeUsageFor(it.binaryName, typeArguments = it.typeArguments) })
    }


internal
val ApiTypeUsage.isStarProjectionTypeUsage
    get() = this === starProjectionTypeUsage


internal
val starProjectionTypeUsage = ApiTypeUsage("*")


private
fun ApiTypeProvider.Context.apiTypeParametersFor(visitedSignature: BaseSignatureVisitor?): List<ApiTypeUsage> =
    visitedSignature?.typeParameters?.map { (binaryName, bounds) -> apiTypeUsageFor(binaryName, bounds = bounds) }
        ?: emptyList()


private
fun ApiTypeProvider.Context.apiFunctionParametersFor(function: ApiFunction, delegate: MethodNode, visitedSignature: MethodSignatureVisitor) =
    delegate.visibleParameterAnnotations?.map { it.has<Nullable>() }.let { parametersNullability ->
        val names by lazy(NONE) {
            parameterNamesFor(
                function.owner.sourceName,
                function.name,
                visitedSignature.parameters.map { if (it.isArray) "${it.typeArguments.single().binaryName}[]" else it.binaryName })
        }
        visitedSignature.parameters.mapIndexed { idx, p ->
            val isNullable = parametersNullability?.get(idx) == true
            ApiFunctionParameter(idx, { names?.get(idx) }, apiTypeUsageFor(p.binaryName, isNullable, p.typeArguments))
        }
    }


private
fun ApiTypeProvider.Context.apiTypeUsageForReturnType(delegate: MethodNode, returnType: TypeSignatureVisitor) =
    apiTypeUsageFor(returnType.binaryName, delegate.visibleAnnotations.has<Nullable>(), returnType.typeArguments)


private
inline fun <reified AnnotationType : Any> List<AnnotationNode>?.has() =
    if (this == null) false
    else Type.getDescriptor(AnnotationType::class.java).let { desc -> any { it.desc == desc } }


private
class ApiTypeClassNode : ClassNode(ASM6) {

    override fun visitSource(file: String?, debug: String?) = Unit
    override fun visitOuterClass(owner: String?, name: String?, desc: String?) = Unit
    override fun visitTypeAnnotation(typeRef: Int, typePath: TypePath?, desc: String?, visible: Boolean): AnnotationVisitor? = null
    override fun visitAttribute(attr: Attribute?) = Unit
    override fun visitInnerClass(name: String?, outerName: String?, innerName: String?, access: Int) = Unit
    override fun visitField(access: Int, name: String?, desc: String?, signature: String?, value: Any?): FieldVisitor? = null
}


private
abstract class BaseSignatureVisitor : SignatureVisitor(ASM6) {

    val typeParameters: MutableMap<String, MutableList<TypeSignatureVisitor>> = LinkedHashMap(1)

    private
    var currentTypeParameter: String? = null

    override fun visitFormalTypeParameter(binaryName: String) {
        typeParameters[binaryName] = ArrayList(1)
        currentTypeParameter = binaryName
    }

    override fun visitClassBound(): SignatureVisitor =
        TypeSignatureVisitor().also { typeParameters[currentTypeParameter]!!.add(it) }

    override fun visitInterfaceBound(): SignatureVisitor =
        TypeSignatureVisitor().also { typeParameters[currentTypeParameter]!!.add(it) }
}


private
class ClassSignatureVisitor : BaseSignatureVisitor()


private
class MethodSignatureVisitor : BaseSignatureVisitor() {

    val parameters: MutableList<TypeSignatureVisitor> = ArrayList(1)

    val returnType = TypeSignatureVisitor()

    override fun visitParameterType(): SignatureVisitor =
        TypeSignatureVisitor().also { parameters.add(it) }

    override fun visitReturnType(): SignatureVisitor =
        returnType
}


private
class TypeSignatureVisitor : SignatureVisitor(ASM6) {

    var isArray = false

    lateinit var binaryName: String

    val typeArguments = ArrayList<TypeSignatureVisitor>(1)

    private
    var expectTypeArgument = false

    override fun visitBaseType(descriptor: Char) =
        visitBinaryName(binaryNameOfBaseType(descriptor))

    override fun visitArrayType(): SignatureVisitor =
        TypeSignatureVisitor().also {
            visitBinaryName("kotlin.Array")
            isArray = true
            typeArguments.add(it)
        }

    override fun visitClassType(internalName: String) =
        visitBinaryName(binaryNameOfInternalName(internalName))

    override fun visitInnerClassType(localName: String) {
        binaryName += "${'$'}$localName"
    }

    override fun visitTypeArgument() {
        typeArguments.add(TypeSignatureVisitor().also { it.binaryName = "?" })
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor =
        TypeSignatureVisitor().also {
            expectTypeArgument = true
            typeArguments.add(it)
        }

    override fun visitTypeVariable(internalName: String) {
        visitBinaryName(binaryNameOfInternalName(internalName))
    }

    private
    fun visitBinaryName(binaryName: String) {
        if (expectTypeArgument) {
            TypeSignatureVisitor().let {
                typeArguments.add(it)
                SignatureReader(binaryName).accept(it)
            }
            expectTypeArgument = false
        } else {
            this.binaryName = binaryName
        }
    }
}


private
fun binaryNameOfBaseType(descriptor: Char) =
    Type.getType(descriptor.toString()).className


private
fun binaryNameOfInternalName(internalName: String): String =
    Type.getObjectType(internalName).className


private
fun sourceNameOfBinaryName(binaryName: String): String =
    when (binaryName) {
        "void" -> "Unit"
        "?" -> "*"
        in mappedTypeStrings.keys -> mappedTypeStrings[binaryName]!!
        in primitiveTypeStrings.keys -> primitiveTypeStrings[binaryName]!!
        else -> binaryName.replace('$', '.')
    }


/**
 * See https://kotlinlang.org/docs/reference/java-interop.html#mapped-types
 */
private
val mappedTypeStrings =
    mapOf(
        // Built-ins
        "java.lang.Cloneable" to "kotlin.Cloneable",
        "java.lang.Comparable" to "kotlin.Comparable",
        "java.lang.Enum" to "kotlin.Enum",
        "java.lang.Annotation" to "kotlin.Annotation",
        "java.lang.Deprecated" to "kotlin.Deprecated",
        "java.lang.CharSequence" to "kotlin.CharSequence",
        "java.lang.Number" to "kotlin.Number",
        "java.lang.Throwable" to "kotlin.Throwable",
        // Collections
        "java.util.Iterable" to "kotlin.collections.Iterable",
        "java.util.Iterator" to "kotlin.collections.Iterator",
        "java.util.ListIterator" to "kotlin.collections.ListIterator",
        "java.util.Collection" to "kotlin.collections.Collection",
        "java.util.List" to "kotlin.collections.List",
        "java.util.ArrayList" to "kotlin.collections.ArrayList",
        "java.util.Set" to "kotlin.collections.Set",
        "java.util.HashSet" to "kotlin.collections.HashSet",
        "java.util.LinkedHashSet" to "kotlin.collections.LinkedHashSet",
        "java.util.Map" to "kotlin.collections.Map",
        "java.util.Map.Entry" to "kotlin.collections.Map.Entry",
        "java.util.HashMap" to "kotlin.collections.HashMap",
        "java.util.LinkedHashMap" to "kotlin.collections.LinkedHashMap")
