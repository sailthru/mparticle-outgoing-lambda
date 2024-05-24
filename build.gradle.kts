import com.sailthru.gradle.ProjectType

plugins {
    id("java")
    id("com.sailthru.gradle") version("v0.7.0")
}

group = "com.sailthru"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

sailthru {
    type = ProjectType.CONTAINER_SERVICE
    javaVersion = JavaVersion.VERSION_1_8
    checkstyleEnabled = false
}
