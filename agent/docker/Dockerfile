# syntax=docker/dockerfile:1.7
ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG RUNTIME_IMAGE=busybox:1.37.0

FROM --platform=$BUILDPLATFORM ${MAVEN_IMAGE} AS builder

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -pl :pole-java-agent -am package -DskipTests -Dsource.skip=true -Dmaven.javadoc.skip=true

RUN set -eux; \
    set -- $(find . -type f -path '*/target/pole-java-agent-*.jar' ! -name 'original-*' ! -name '*-sources.jar' ! -name '*-javadoc.jar'); \
    test "$#" -eq 1; \
    jar tf "$1" | grep -qx 'META-INF/MANIFEST.MF'; \
    install -D -m 0644 "$1" /out/pole-java-agent.jar

FROM --platform=$TARGETPLATFORM ${RUNTIME_IMAGE}

WORKDIR /opt/pole/java-agent
COPY --from=builder /out/pole-java-agent.jar ./pole-java-agent.jar

RUN test -s /opt/pole/java-agent/pole-java-agent.jar && test -r /opt/pole/java-agent/pole-java-agent.jar
