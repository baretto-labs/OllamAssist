plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
    id("org.sonarqube") version "4.3.0.3225"
}

group = "fr.baretto"
version = "1.10.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}
val langchain4jEasyRag = "1.8.0-beta15"
val langchain4jVersion = "1.8.0"
val mockitoVersion = "5.19.0"
val lombokVersion = "1.18.38"
val junitJupiterVersion = "5.11.0-M2"
val junitVintageVersion = "5.11.0-M2"
val junitEngineVersion = "5.11.4"
val junitLegacyVersion = "4.13.2"
val assertjVersion = "3.27.0"
val testcontainersVersion = "1.21.3"
val rsyntaxtextareaVersion = "3.6.0"
val plexusVersion = "4.0.2"
val jsoupVersion = "1.21.2"
val jacksonVersion = "2.20.1"
val djlVersion = "0.28.0"
sourceSets {
    create("benchmark") {
        java.srcDir("src/benchmark/java")
        resources.srcDir("src/benchmark/resources")
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }

}

configurations {
    maybeCreate("benchmarkImplementation").extendsFrom(
        configurations["implementation"],
        configurations["testImplementation"]
    )
    maybeCreate("benchmarkRuntimeOnly").extendsFrom(configurations["runtimeOnly"], configurations["testRuntimeOnly"])
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3", useInstaller = true)
        bundledPlugins("Git4Idea")
    }

    implementation("ai.djl:api:$djlVersion")
    implementation("ai.djl.huggingface:tokenizers:$djlVersion")

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
        // Exclude heavy Apache Tika parsers not needed for indexing code files
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

    implementation("dev.langchain4j:langchain4j-agentic:$langchain4jEasyRag")
    implementation("dev.langchain4j:langchain4j-agentic-a2a:$langchain4jEasyRag")
    runtimeOnly("org.slf4j:slf4j-jdk14:1.7.36")
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

    add("benchmarkImplementation", "org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    add("benchmarkImplementation", "org.testcontainers:junit-jupiter:$testcontainersVersion")
    add("benchmarkImplementation", "org.testcontainers:postgresql:$testcontainersVersion")
}



intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"
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

    // Disable buildSearchableOptions in development for faster builds
    // It will be enabled automatically in CI or when PUBLISH_TOKEN is set
    buildSearchableOptions.set(System.getenv("CI") != null || System.getenv("PUBLISH_TOKEN") != null)
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

    sonar {
        properties {
            property("sonar.projectKey", "baretto-labs_OllamAssist")
            property("sonar.organization", "baretto-labs")
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.login", System.getenv("SONAR_TOKEN"))
        }
    }
}
