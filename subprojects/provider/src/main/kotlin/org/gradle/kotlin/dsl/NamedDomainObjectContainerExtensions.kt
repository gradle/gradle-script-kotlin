/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.PolymorphicDomainObjectContainer

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * Allows the container to be configured, creating missing objects as they are referenced.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 */
inline operator fun <T : Any, C : NamedDomainObjectContainer<T>> C.invoke(
    configuration: NamedDomainObjectContainerScope<T>.() -> Unit
): C =

    apply {
        configuration(NamedDomainObjectContainerScope(this))
    }


/**
 * Receiver for [NamedDomainObjectContainer] configuration blocks.
 */
class NamedDomainObjectContainerScope<T : Any>(
    private val container: NamedDomainObjectContainer<T>
) : NamedDomainObjectContainer<T> by container, PolymorphicDomainObjectContainer<T> {

    override fun <U : T> create(name: String, type: Class<U>): U =
        polymorphicDomainObjectContainer().create(name, type)

    override fun <U : T> create(name: String, type: Class<U>, configuration: Action<in U>): U =
        polymorphicDomainObjectContainer().create(name, type, configuration)

    override fun <U : T> maybeCreate(name: String, type: Class<U>): U =
        polymorphicDomainObjectContainer().maybeCreate(name, type)

    override fun <U : T> containerWithType(type: Class<U>): NamedDomainObjectContainer<U> =
        polymorphicDomainObjectContainer().containerWithType(type)

    /**
     * @see [NamedDomainObjectContainer.getByName]
     */
    inline operator fun String.invoke(configuration: T.() -> Unit): T =
        this().apply(configuration)

    /**
     * @see [NamedDomainObjectContainer.getByName]
     */
    operator fun String.invoke(): T =
        container.getByName(this)

    /**
     * @see [PolymorphicDomainObjectContainer.getByName]
     */
    inline operator fun <U : T> String.invoke(type: KClass<U>, configuration: U.() -> Unit): U =
        this(type).apply(configuration)

    /**
     * @see [PolymorphicDomainObjectContainer.getByName]
     */
    operator fun <U : T> String.invoke(type: KClass<U>): U =
        polymorphicDomainObjectContainer().getByName(this, type)

    /**
     * Cast this to [PolymorphicDomainObjectContainer] or throw [IllegalArgumentException].
     *
     * We must rely on the dynamic cast and possible runtime failure here due to a Kotlin extension member limitation.
     * Kotlin currently can't disambiguate between invoke operators with more specific receivers in a type hierarchy.
     *
     * See https://youtrack.jetbrains.com/issue/KT-15711
     */
    private
    fun polymorphicDomainObjectContainer() =
        container as? PolymorphicDomainObjectContainer<T>
            ?: throw IllegalArgumentException("Container '$container' is not polymorphic.")
}


/**
 * Provides a property delegate that creates elements of the default collection type.
 */
val <T : Any> NamedDomainObjectContainer<T>.creating
    get() = NamedDomainObjectContainerDelegateProvider(this, {})


/**
 * Provides a property delegate that creates elements of the default collection type with the given [configuration].
 *
 * `val myElement by myContainer.creating { myProperty = 42 }`
 */
fun <T : Any> NamedDomainObjectContainer<T>.creating(configuration: T.() -> Unit) =
    NamedDomainObjectContainerDelegateProvider(this, configuration)


/**
 * A property delegate that creates elements in the given [NamedDomainObjectContainer].
 *
 * See [creating]
 */
class NamedDomainObjectContainerDelegateProvider<T : Any>(
    val container: NamedDomainObjectContainer<T>,
    val configuration: T.() -> Unit
) {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        container.apply {
            create(property.name).apply(configuration)
        }
}


/**
 * Provides a property delegate that creates elements of the given [type].
 */
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: KClass<U>) =
    creating(type.java, {})


/**
 * Provides a property delegate that creates elements of the given [type] with the given [configuration].
 */
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: KClass<U>, configuration: U.() -> Unit) =
    creating(type.java, configuration)


/**
 * Provides a property delegate that creates elements of the given [type] expressed as a [java.lang.Class]
 * with the given [configuration].
 */
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: Class<U>, configuration: U.() -> Unit) =
    PolymorphicDomainObjectContainerDelegateProvider(this, type, configuration)


/**
 * A property delegate that creates elements of the given [type] with the given [configuration] in the given [container].
 */
class PolymorphicDomainObjectContainerDelegateProvider<T : Any, U : T>(
    val container: PolymorphicDomainObjectContainer<T>,
    val type: Class<U>,
    val configuration: U.() -> Unit
) {

    @Suppress("unchecked_cast")
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        container.apply {
            create(property.name, type).apply(configuration)
        } as PolymorphicDomainObjectContainer<U>
}


/**
 * Provides a property delegate that gets elements of the given [type] and applies the given [configuration].
 */
fun <T : Any, U : T> NamedDomainObjectContainer<T>.getting(type: KClass<U>, configuration: U.() -> Unit) =
    PolymorphicDomainObjectContainerGettingDelegateProvider(this, type, configuration)


/**
 * Provides a property delegate that gets elements of the given [type].
 */
fun <T : Any, U : T> NamedDomainObjectContainer<T>.getting(type: KClass<U>) =
    PolymorphicDomainObjectContainerGettingDelegate(this, type)


/**
 * A property delegate that gets elements of the given [type] in the given [container].
 */
class PolymorphicDomainObjectContainerGettingDelegate<T : Any, U : T>(
    val container: NamedDomainObjectContainer<T>,
    val type: KClass<U>
) {

    operator fun getValue(receiver: Any?, property: kotlin.reflect.KProperty<*>): U =
        container.getByName(property.name, type)
}


/**
 * A property delegate that gets elements of the given [type] from the given [container]
 * and applies the given [configuration].
 */
class PolymorphicDomainObjectContainerGettingDelegateProvider<T : Any, U : T>(
    val container: NamedDomainObjectContainer<T>,
    val type: KClass<U>,
    val configuration: U.() -> Unit
) {

    @Suppress("unchecked_cast")
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        container.apply {
            getByName(property.name, type).configuration()
        } as NamedDomainObjectContainer<U>
}
