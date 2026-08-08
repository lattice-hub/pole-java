package io.github.latticehub.agent.plugin.springcloud.v3x;

import io.github.latticehub.agent.plugin.springcloud.SpringCloudVersionPlugin;

public final class SpringCloud3xPlugin implements SpringCloudVersionPlugin {
    @Override
    public String id() {
        return "spring-cloud-3x";
    }

    @Override
    public int springBootMajor() {
        return 2;
    }

    @Override
    public int springCloudMajor() {
        return 3;
    }

    @Override
    public String adapterPackage() {
        return "io.github.latticehub.adapter.springcloud.boot2";
    }

    @Override
    public String initializerClassName() {
        return "io.github.latticehub.adapter.springcloud.boot2.PoleSpringBoot2Initializer";
    }
}
