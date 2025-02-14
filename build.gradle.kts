plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "fr.baretto"
version = "1.0.3"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    val langchain4jVersion = "1.0.0-alpha1"
    val apacheLuceneVersion = "9.12.1"

    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-core:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-easy-rag:$langchain4jVersion") {
        exclude(group = "xml-apis")
    }
    implementation("dev.langchain4j:langchain4j-reactor:$langchain4jVersion")

    implementation("org.apache.lucene:lucene-core:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-codecs:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-highlighter:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-memory:$apacheLuceneVersion")

    implementation("com.fifesoft:rsyntaxtextarea:3.5.3")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0-M2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")

    testImplementation("junit:junit:4.13.2")

    testImplementation("org.junit.vintage:junit-vintage-engine:5.11.0-M2")

    testImplementation("org.assertj:assertj-core:3.26.0")
}

intellij {
    version.set("2024.3")
    type.set("IC")
    plugins.set(listOf("java"))
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

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }
    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
    }
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}