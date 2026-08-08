#!/usr/bin/env sh
set -eu

image_reference=${1:?"usage: verify-image.sh IMAGE [linux/architecture]"}
expected_platform=${2:-}
agent_path=/opt/pole/java-agent/pole-java-agent.jar

if [ -n "$expected_platform" ]; then
    actual_platform=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' "$image_reference")
    if [ "$actual_platform" != "$expected_platform" ]; then
        printf 'expected image platform %s, got %s\n' "$expected_platform" "$actual_platform" >&2
        exit 1
    fi
    docker run --rm --platform "$expected_platform" --entrypoint /bin/sh "$image_reference" -eu -c "test -s $agent_path && test -r $agent_path"
    exit 0
fi

docker run --rm --entrypoint /bin/sh "$image_reference" -eu -c "test -s $agent_path && test -r $agent_path"
