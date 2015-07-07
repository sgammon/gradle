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

package org.gradle.language.java
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

class JavaLanguageCustomLibraryDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can depend on a custom component producing a JVM library"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary)
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

    }

    @Unroll
    def "can depend on a custom component producing a JVM library in another project with dependency {#dependency}"() {
        given:
        applyJavaPlugin(buildFile)
        file('settings.gradle') << 'include "sub"'

        def subBuildFile = file('sub/build.gradle')
        subBuildFile << '''
plugins {
    id 'jvm-component'
}
'''
        addCustomLibraryType(subBuildFile)
        subBuildFile << '''
model {
    components {
        zdep(CustomLibrary)
    }
}
'''
        buildFile << """

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        $dependency
                    }
                }
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.contains(':sub:zdepJar')
            }
        }
    }
}
"""
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

        where:
        dependency << ["project ':sub' library 'zdep'","project ':sub'"]
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "can depend on a custom component producing a JVM library with corresponding platform"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        java6MainJar.finalizedBy('checkDependencies')
        java7MainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileJava6MainJarMainJava.taskDependencies.getDependencies(compileJava6MainJarMainJava).contains(zdepjava6Jar)
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(zdepjava7Jar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':java6MainJar'
        succeeds ':java7MainJar'

    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should fail resolving dependencies only for the missing dependency variant"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary) {
            targetPlatform 'java7'
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        java7MainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(zdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect: 'The Java 7 variant of the main jar can be built'
        succeeds ':java7MainJar'

        and: 'the Java 6 variant fails'
        fails ':java6MainJar'

        and: 'error message indicates the available platforms for the target dependency'
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'java6MainJar'' source set 'Java source 'main:java''")
        failure.assertHasCause("Cannot find a compatible binary for library 'zdep' (Java SE 6). Available platforms: [Java SE 7]")

    }


    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should choose the highest variant of the target binary when dependency is a JVM component"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
        main(CustomLibrary) {
            targetPlatform 'java7'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(java7ZdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should choose the highest variant of the target binary when dependency is a custom component"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepjava7Jar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

    }

    def "custom component can consume a JVM library"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec)
        zdep(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }

    tasks {
        zdepJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileZdepJarZdepJava.taskDependencies.getDependencies(compileZdepJarZdepJava).contains(mainJar)
            }
        }
    }
}
'''
        file('src/zdep/java/App.java') << 'public class App extends TestApp {}'
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':zdepJar'

    }

    void applyJavaPlugin(File buildFile) {
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}
'''
    }

    void addCustomLibraryType(File buildFile) {
        buildFile << '''
import org.gradle.internal.service.ServiceRegistry
import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.platform.base.internal.DefaultPlatformRequirement

interface CustomLibrary extends LibrarySpec {
    void targetPlatform(String platform)
    List<String> getTargetPlatforms()
}

class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary {
    List<String> targetPlatforms = []
    void targetPlatform(String platform) { targetPlatforms << platform }
}

            class ComponentTypeRules extends RuleSource {

                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomLibrary> builder) {
                    builder.defaultImplementation(DefaultCustomLibrary)
                }

                @ComponentBinaries
                void createBinaries(ModelMap<JarBinarySpec> binaries,
                    CustomLibrary jvmLibrary,
                    PlatformResolvers platforms,
                    @Path("buildDir") File buildDir,
                    JavaToolChainRegistry toolChains) {

                    def binariesDir = new File(buildDir, "jars")
                    def classesDir = new File(buildDir, "classes")
                    def targetPlatforms = jvmLibrary.targetPlatforms
                    def selectedPlatforms = targetPlatforms.collect { platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create(it)) }?:[new DefaultJavaPlatform(JavaVersion.current())]
                    def multipleTargets = selectedPlatforms.size()>1
                    selectedPlatforms.each { platform ->
                        def toolChain = toolChains.getForPlatform(platform)
                        def binaryName = "${jvmLibrary.name}${multipleTargets?platform.name:''}Jar"
                        binaries.create(binaryName) { jar ->
                            jar.baseName = jvmLibrary.name
                            jar.toolChain = toolChain
                            jar.targetPlatform = platform

                            def outputDir = new File(classesDir, jar.name)
                            jar.classesDir = outputDir
                            jar.resourcesDir = outputDir
                            jar.jarFile = new File(binariesDir, "${jar.name}/${jar.baseName}.jar")
                        }
                    }
                }

            }

            apply type: ComponentTypeRules
        '''
    }
}
