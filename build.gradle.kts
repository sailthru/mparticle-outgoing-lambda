import com.sailthru.gradle.ProjectType

plugins {
    id("java")
    id("com.sailthru.gradle") version("v0.9.0")
}

sailthru {
    type = ProjectType.LAMBDA
    javaVersion = JavaVersion.VERSION_21
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

