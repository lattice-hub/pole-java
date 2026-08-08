# syntax=docker/dockerfile:1.7
ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG RUNTIME_IMAGE=busybox:1.37.0

FROM --platform=$BUILDPLATFORM ${MAVEN_IMAGE} AS builder

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -pl :pole-java-agent -am package -DskipTests -Dsource.skip=true -Dmaven.javadoc.skip=true

RUN set -eux; \
    set -- $(find agent/pole-java-agent/target -maxdepth 1 -type f -name 'pole-java-agent-*-distribution.zip'); \
    test "$#" -eq 1; \
    archive="$(readlink -f "$1")"; \
    mkdir -p /out; \
    cd /out; \
    jar xf "$archive"; \
    test -s /out/pole-java-agent/pole-java-agent.jar; \
    test -n "$(find /out/pole-java-agent/lib -name 'pole-agent-core-*.jar' -print -quit)"; \
    test -n "$(find /out/pole-java-agent/plugins -name 'pole-agent-plugin-spring-cloud-*.jar' -print -quit)"

FROM --platform=$TARGETPLATFORM ${RUNTIME_IMAGE}

WORKDIR /opt/pole/java-agent
COPY --from=builder /out/pole-java-agent/ ./

RUN test -s /opt/pole/java-agent/pole-java-agent.jar \
    && test -d /opt/pole/java-agent/lib \
    && test -d /opt/pole/java-agent/plugins
