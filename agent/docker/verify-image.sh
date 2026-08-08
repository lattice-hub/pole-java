#!/usr/bin/env sh
set -eu

image_reference=${1:?"usage: verify-image.sh IMAGE [linux/architecture]"}
expected_platform=${2:-}
agent_home=/opt/pole/java-agent
verify_command="test -s $agent_home/pole-java-agent.jar && test -n \"\$(find $agent_home/lib -name 'pole-agent-core-*.jar' -print -quit)\" && test -n \"\$(find $agent_home/plugins -name 'pole-agent-plugin-spring-cloud-*.jar' -print -quit)\""

if [ -n "$expected_platform" ]; then
    actual_platform=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image_reference")
    if [ "$actual_platform" != "$expected_platform" ]; then
        printf 'expected image platform %s, got %s\n' "$expected_platform" "$actual_platform" >&2
        exit 1
    fi
    docker run --rm --platform "$expected_platform" --entrypoint /bin/sh "$image_reference" -eu -c "$verify_command"
    exit 0
fi

docker run --rm --entrypoint /bin/sh "$image_reference" -eu -c "$verify_command"
