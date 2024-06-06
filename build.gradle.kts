//import com.sailthru.gradle.ProjectType

plugins {
    id("java")
//    id("com.sailthru.gradle") version("v0.7.0")
}

group = "com.sailthru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation("com.amazonaws:aws-java-sdk-sqs:1.12.475") {
        exclude(group = "software.amazon.ion", module = "ion-java")
    }
    implementation("org.slf4j:slf4j-simple:1.7.32")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Zip>("buildZip") {
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}

//sailthru {
//    type = ProjectType.CONTAINER_SERVICE
//    javaVersion = JavaVersion.VERSION_1_8
//    checkstyleEnabled = false
//}
