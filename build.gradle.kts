plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.sonarqube") version "7.3.0.8198"
}

group = "fr.baretto"
version = "1.12.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}
val langchain4jEasyRag = "1.13.1-beta23"
val langchain4jVersion = "1.13.1"
val mockitoVersion = "5.19.0"
val lombokVersion = "1.18.38"
val junitJupiterVersion = "5.11.0-M2"
val junitVintageVersion = "5.11.0-M2"
val junitEngineVersion = "5.11.4"
val junitLegacyVersion = "4.13.2"
val assertjVersion = "3.27.0"
val testcontainersVersion = "1.21.4"
val rsyntaxtextareaVersion = "3.6.0"
val plexusVersion = "4.0.2"
val jsoupVersion = "1.22.2"
val jacksonVersion = "2.20.1"
val djlVersion = "0.28.0"

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3", useInstaller = true)
        bundledPlugins("Git4Idea", "com.intellij.java")
        // Required for BasePlatformTestCase, CodeInsightTestFixture, etc.
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    implementation("org.apache.lucene:lucene-queryparser:9.10.0") {
        exclude(group = "org.apache.lucene")
    }

    implementation("ai.djl:api:$djlVersion") {
        exclude(group = "org.slf4j")
    }
    implementation("ai.djl.huggingface:tokenizers:$djlVersion") {
        exclude(group = "org.slf4j")
    }

    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion"){
        exclude(group = "org.apache.lucene")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-core:$langchain4jVersion"){
        exclude(group = "org.apache.lucene")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion"){
        exclude(group = "org.apache.lucene")
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-easy-rag:$langchain4jEasyRag") {
        exclude(group = "xml-apis")
        exclude(group = "ai.djl", module = "api")
        exclude(group = "ai.djl.huggingface", module = "tokenizers")
        exclude(group = "org.apache.lucene")
        exclude(group = "org.slf4j")
        exclude(group = "org.apache.tika", module = "tika-parser-microsoft-module")
        exclude(group = "org.apache.tika", module = "tika-parser-miscoffice-module")
        exclude(group = "org.apache.tika", module = "tika-parser-pdf-module")
        exclude(group = "org.apache.tika", module = "tika-parser-image-module")
        exclude(group = "org.apache.tika", module = "tika-parser-video-module")
        exclude(group = "org.apache.tika", module = "tika-parser-audio-module")
        exclude(group = "org.apache.tika", module = "tika-parser-ocr-module")
        exclude(group = "org.apache.tika", module = "tika-parser-news-module")
        exclude(group = "org.apache.tika", module = "tika-parser-webarchive-module")
    }
    implementation("dev.langchain4j:langchain4j-reactor:$langchain4jEasyRag") {
        exclude(group = "org.apache.lucene")
        exclude(group = "org.slf4j")
    }

    implementation("dev.langchain4j:langchain4j-agentic:$langchain4jEasyRag") {
        exclude(group = "org.slf4j")
    }
    implementation("dev.langchain4j:langchain4j-agentic-a2a:$langchain4jEasyRag") {
        exclude(group = "org.slf4j")
    }
    implementation("org.codehaus.plexus:plexus-utils:$plexusVersion")
    implementation("org.jsoup:jsoup:$jsoupVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonVersion")

    implementation("com.fifesoft:rsyntaxtextarea:$rsyntaxtextareaVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("junit:junit:$junitLegacyVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.junit.vintage:junit-vintage-engine:$junitVintageVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitEngineVersion")

    // Testcontainers — used by platform tests to spin up a real Ollama instance
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:ollama:$testcontainersVersion")
}



intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
        }
        changeNotes = "Fix: Lucene IndexWriterConfig sharing violation"
    }

    signing {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    buildSearchableOptions.set(System.getenv("CI") != null || System.getenv("PUBLISH_TOKEN") != null)

    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.7")
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2025.1.2")
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        // Required for LangChain4j @Tool parameter name resolution at runtime.
        // Without this flag, tool schemas use arg0/arg1 instead of the actual
        // parameter names, causing the model to receive meaningless argument names.
        options.compilerArgs.add("-parameters")
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    patchPluginXml {
        sinceBuild.set("243")
    }

    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
            excludeTags("benchmark")
        }
        // Platform tests (BasePlatformTestCase) require the IntelliJ runtime —
        // they run via the platformTest task, not here.
        exclude("**/platform/**")
    }

    check {
        dependsOn("benchmark")
    }

    build {
        dependsOn("buildPlugin")
    }

    sonar {
        properties {
            property("sonar.projectKey", "baretto-labs_OllamAssist")
            property("sonar.organization", "baretto-labs")
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.login", System.getenv("SONAR_TOKEN"))
        }
    }
}

intellijPlatformTesting {
    testIde {
        register("benchmark") {
            task {
                group = "verification"
                description = "Runs LLM-as-a-judge benchmark tests (tagged @benchmark)."
                useJUnitPlatform {
                    includeTags("benchmark")
                }
                shouldRunAfter(tasks.test)
                systemProperties(
                    project.properties.filterKeys { it.startsWith("benchmark.") }
                )
            }
        }

        // Platform integration tests: run inside the IntelliJ runtime so BasePlatformTestCase
        // has access to VirtualFile, WriteCommandAction, MessageBus, PsiManager, etc.
        // Usage: ./gradlew platformTest
        // Skips tests annotated with @RequiresOllama if Ollama is not reachable.
        register("platformTest") {
            task {
                group = "verification"
                description = "Runs IntelliJ Platform integration tests (BasePlatformTestCase)."
                include("**/platform/**")
                shouldRunAfter(tasks.test)
                systemProperty("platformTest.ollamaUrl",
                    project.properties.getOrDefault("platformTest.ollamaUrl", "http://localhost:11434").toString())
            }
        }
    }
}
