# mparticle-outgoing-lambda

This project contains the implementation of AWS Lambda functions for processing outgoing sqs messages from mParticle.

## Prerequisites

- **Java Development Kit (JDK) 21 or later**
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

## Testing in sandbox

Testing in sandbox uses localstack. It's pretty straightforward:

1. `./gradlew buildZip`
2. `doco up -d mparticle-outgoing-lambda`

Getting the logs from that thing is a lot more hassle though. Recommended approach:

1. `docker ps |grep localstack-lambda-mparticle-outgoing-message-processor` - this should give you the Docker container which is running the processor
2. `docker logs -f <container_name>`

## Debugging in sandbox

You'll have much better luck writing a unit test to reproduce the condition. Really.

It's _somewhat_ possible to debug but it's a bit of a pain. Basically:

1. Edit the script in `devtools/containers/localstack/lambda/mparticle-outgoing.sh` with `suspend=y` (or alternatively send a _ton_ of messages)
2. `docker ps |grep localstack-lambda-mparticle-outgoing-message-processor`. You will see a port mapping that will look like this: `0.0.0.0:64171->9929/tcp`. The number after the first colon is the debug port.
3. Set up IntelliJ for remote debugging on that port
4. Place your breakpoints and start your debugger

There is very very little time to connect - as soon as the lambda ends, the port will not allow connection anymore.
Also, you have 900 seconds to connect when suspend=y before it dies on you, so be quick!