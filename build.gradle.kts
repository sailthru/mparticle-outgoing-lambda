plugins {
    id("java")
    id("com.sailthru.gradle") version("v0.8.0")
}

group = "com.sailthru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.25.60"))
    implementation("software.amazon.awssdk:lambda")
    implementation("software.amazon.awssdk:sqs")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Zip>("buildZip") {
    dependsOn(tasks.classes)
    from(sourceSets.main.get().output)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}
