plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "fr.baretto"
version = "1.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}


dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3", useInstaller = true)
        bundledPlugins("Git4Idea")
    }

    val langchain4jVersion = "1.0.1-beta6"
    val apacheLuceneVersion = "9.12.1"
    val mockitoVersion = "5.16.1"

    implementation("dev.langchain4j:langchain4j-ollama:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-core:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-easy-rag:$langchain4jVersion") {
        exclude(group = "xml-apis")
    }
    implementation("dev.langchain4j:langchain4j-reactor:$langchain4jVersion")
    implementation("org.codehaus.plexus:plexus-utils:3.4.1")
    implementation("org.apache.lucene:lucene-core:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-codecs:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-highlighter:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$apacheLuceneVersion")
    implementation("org.apache.lucene:lucene-memory:$apacheLuceneVersion")

    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")


    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0-M2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.11.0-M2")
    testImplementation("org.assertj:assertj-core:3.27.0")

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
}