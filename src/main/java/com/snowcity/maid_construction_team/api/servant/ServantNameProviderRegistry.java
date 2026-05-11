package com.snowcity.maid_construction_team.api.servant;

/**
 * 仆从命名提供器的全局注册表。
 * 默认使用 {@link AlphanumericServantNameProvider}。
 */
public class ServantNameProviderRegistry {

    private static IServantNameProvider provider = new AlphanumericServantNameProvider();

    /**
     * 获取当前使用的命名器。
     */
    public static IServantNameProvider getProvider() {
        return provider;
    }

    /**
     * 设置新的命名器。
     */
    public static void setProvider(IServantNameProvider newProvider) {
        provider = newProvider;
    }
}