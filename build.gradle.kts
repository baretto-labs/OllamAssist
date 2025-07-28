plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "fr.baretto"
version = "1.3.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val langchain4jVersion = "1.1.0-rc1"
val apacheLuceneVersion = "9.12.1"
val mockitoVersion = "5.16.1"
val lombokVersion = "1.18.38"
val junitJupiterVersion = "5.11.0-M2"
val junitVintageVersion = "6.0.0-M2"
val junitEngineVersion = "5.11.4"
val junitLegacyVersion = "4.13.2"
val assertjVersion = "3.27.0"
val testcontainersVersion = "1.21.3"
val rsyntaxtextareaVersion = "3.6.0"

sourceSets {
    create("benchmark") {
        java.srcDir("src/benchmark/java")
        resources.srcDir("src/benchmark/resources")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }

}

configurations {
    maybeCreate("benchmarkImplementation").extendsFrom(configurations["implementation"], configurations["testImplementation"])
    maybeCreate("benchmarkRuntimeOnly").extendsFrom(configurations["runtimeOnly"], configurations["testRuntimeOnly"])
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3", useInstaller = true)
        bundledPlugins("Git4Idea")
    }

    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-core:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-easy-rag:1.1.0-beta7") {
        exclude(group = "xml-apis")
    }
    implementation("dev.langchain4j:langchain4j-reactor:1.1.0-beta7")
    implementation("org.codehaus.plexus:plexus-utils:3.4.1")

    implementation("org.apache.lucene:lucene-core:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-codecs:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-highlighter:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-memory:$apacheLuceneVersion")

    implementation("com.fifesoft:rsyntaxtextarea:$rsyntaxtextareaVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("junit:junit:$junitLegacyVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.junit.vintage:junit-vintage-engine:$junitVintageVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitEngineVersion")

    add("benchmarkImplementation", "org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    add("benchmarkImplementation", "org.testcontainers:junit-jupiter:$testcontainersVersion")
    add("benchmarkImplementation", "org.testcontainers:postgresql:$testcontainersVersion")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
            untilBuild = ""
        }
        changeNotes = "Mise à jour vers IntelliJ Platform Gradle Plugin 2.6.0"
    }

    signing {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    buildSearchableOptions.set(true)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("")
    }

    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
    }

    val benchmark by registering(Test::class) {
        description = "Runs benchmark tests."
        group = "verification"
        testClassesDirs = sourceSets["benchmark"].output.classesDirs
        classpath = sourceSets["benchmark"].runtimeClasspath
        useJUnitPlatform()
        shouldRunAfter(test)
    }

    check {
        dependsOn(benchmark)
    }

    build {
        dependsOn("buildPlugin")
    }
}