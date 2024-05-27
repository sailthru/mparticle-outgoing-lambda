# mparticle-outgoing-lambda

This project contains the implementation of AWS Lambda functions for processing outgoing sqs messages from mParticle.

## Prerequisites

- **Java Development Kit (JDK) 17 or later**
- **Gradle**
- **AWS CLI** configured with appropriate permissions

## Setup

1. **Login to JVM using the production readonly profile:**

```sh
jvm_login - using prod readonly profile
```

Build the project:

Use Gradle to build the project. This will compile the source code and package it.

```sh
./gradlew build
```
