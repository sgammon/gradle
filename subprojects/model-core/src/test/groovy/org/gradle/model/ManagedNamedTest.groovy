/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model
import org.gradle.api.Named
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.core.ModelRegistrations
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.inspect.ReadonlyImmutableManagedPropertyException
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException

class ManagedNamedTest extends ProjectRegistrySpec {

    def "named struct has name name property populated"() {
        when:
        registry.register(ModelRegistrations.of(registry.path("foo"), nodeInitializerRegistry.getNodeInitializer(NamedThingInterface)).descriptor(registry.desc("foo")).build())

        then:
        registry.realize("foo", NamedThingInterface).name == "foo"

        when:
        registry.register(ModelRegistrations.of(registry.path("bar"), nodeInitializerRegistry.getNodeInitializer(NamedThingInterface)).descriptor(registry.desc("bar")).build())

        then:
        registry.realize("bar", NamedThingInterface).name == "bar"
    }

    @Managed
    static interface NamedThingInterface extends Named {
        String getOther()
        void setOther(String string)
    }

    @Managed
    static abstract class NonNamedThing {
        abstract String getName()

        abstract void setName(String name)
    }

    def "named struct does not have name populated if does not implement named"() {
        when:
        registry.register(ModelRegistrations.of(registry.path("foo"), nodeInitializerRegistry.getNodeInitializer(NonNamedThing)).descriptor(registry.desc("foo")).build())

        then:
        registry.realize("foo", NonNamedThing).name == null
    }

    @Managed
    static abstract class NonNamedThingNoSetter {
        abstract String getName()
    }

    def "name requires setter if not named"() {
        given:
        registry.register(ModelRegistrations.of(registry.path("bar"), nodeInitializerRegistry.getNodeInitializer(NonNamedThingNoSetter)).descriptor(registry.desc("bar")).build())

        when:
        registry.realize("bar", NonNamedThingNoSetter)

        then:
        def ex = thrown(ModelRuleExecutionException)
        ex.cause instanceof ReadonlyImmutableManagedPropertyException
    }

    @Managed
    static abstract class NamedThingWithSetter implements Named {
        abstract String getName()

        abstract void setName(String name)
    }

    def "named cannot have setter"() {
        when:
        schemaStore.getSchema(NamedThingWithSetter)

        then:
        thrown InvalidManagedModelElementTypeException
    }
}
