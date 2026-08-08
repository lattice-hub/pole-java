package io.github.latticehub.agent.plugin.springcloud.v4x;

import io.github.latticehub.agent.plugin.springcloud.SpringCloudVersionPlugin;

public final class SpringCloud4xPlugin implements SpringCloudVersionPlugin {
    @Override
    public String id() {
        return "spring-cloud-4x";
    }

    @Override
    public int springBootMajor() {
        return 3;
    }

    @Override
    public int springCloudMajor() {
        return 4;
    }

    @Override
    public String adapterPackage() {
        return "io.github.latticehub.adapter.springcloud.boot3";
    }

    @Override
    public String initializerClassName() {
        return "io.github.latticehub.adapter.springcloud.boot3.PoleSpringBoot3Initializer";
    }
}
