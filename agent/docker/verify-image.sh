#!/usr/bin/env sh
set -eu

image_reference=${1:?"usage: verify-image.sh IMAGE [linux/architecture]"}
expected_platform=${2:-}
agent_home=/opt/pole/java-agent
spring_cloud_plugins=$agent_home/plugins/spring-cloud-plugins
verify_command="test -s $agent_home/pole-java-agent.jar && test -n \"\$(find $agent_home/lib -name 'pole-agent-core-*.jar' -print -quit)\" && test -n \"\$(find $spring_cloud_plugins -name 'spring-cloud-plugin-*.jar' -print -quit)\" && test -n \"\$(find $spring_cloud_plugins -name 'spring-cloud-3x-plugin-*.jar' -print -quit)\" && test -n \"\$(find $spring_cloud_plugins -name 'spring-cloud-4x-plugin-*.jar' -print -quit)\" && test -n \"\$(find $spring_cloud_plugins -name 'spring-cloud-5x-plugin-*.jar' -print -quit)\" && test -n \"\$(find $agent_home/plugins/dubbo-plugins -name 'dubbo-3x-plugin-*.jar' -print -quit)\" && test -n \"\$(find $agent_home/plugins/grpc-plugins -name 'grpc-1x-plugin-*.jar' -print -quit)\" && test -n \"\$(find $agent_home/plugins/thrift-plugins -name 'thrift-http-plugin-*.jar' -print -quit)\""

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
