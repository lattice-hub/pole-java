package io.github.latticehub.agent.plugin.springcloud.v5x;

import io.github.latticehub.agent.plugin.springcloud.SpringCloudVersionPlugin;

public final class SpringCloud5xPlugin implements SpringCloudVersionPlugin {
    @Override
    public String id() {
        return "spring-cloud-5x";
    }

    @Override
    public int springBootMajor() {
        return 4;
    }

    @Override
    public int springCloudMajor() {
        return 5;
    }

    @Override
    public String adapterPackage() {
        return "io.github.latticehub.adapter.springcloud.boot4";
    }

    @Override
    public String initializerClassName() {
        return "io.github.latticehub.adapter.springcloud.boot4.PoleSpringBoot4Initializer";
    }
}
