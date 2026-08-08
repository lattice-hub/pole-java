package io.github.latticehub.agent.plugin.springcloud;

public interface SpringCloudVersionPlugin {
    String id();

    int springBootMajor();

    int springCloudMajor();

    String adapterPackage();

    String initializerClassName();

    default boolean supports(int bootMajor, Integer cloudMajor) {
        return springBootMajor() == bootMajor && (cloudMajor == null || springCloudMajor() == cloudMajor);
    }
}
