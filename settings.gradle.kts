pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven {
            url = uri("https://sailthru-680305091011.d.codeartifact.us-east-1.amazonaws.com/maven/maven/")
            credentials {
                username = "aws"
                password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
            }
        }
    }
}

rootProject.name = "mparticle-outgoing-lambda"
