package com.snowcity.maid_construction_team.api.labor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 劳动力提供者注册表。
 * <p>
 * 全局单例，管理所有已注册的 {@link ILaborProvider}。
 */
public class LaborProviderRegistry {

    private static final Map<String, ILaborProvider> providers = new ConcurrentHashMap<>();

    /**
     * 注册提供者。若ID已存在则覆盖。
     */
    public static void register(ILaborProvider provider) {
        providers.put(provider.getProviderId(), provider);
    }

    /**
     * 按ID获取提供者。
     */
    public static Optional<ILaborProvider> getProvider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * 获取所有已注册且启用的提供者列表（按注册先后顺序）。
     */
    public static List<ILaborProvider> getEnabledProviders() {
        List<ILaborProvider> list = new ArrayList<>();
        for (ILaborProvider p : providers.values()) {
            if (p.isEnabled()) list.add(p);
        }
        return list;
    }

    /**
     * 获取所有已注册提供者（含未启用）。
     */
    public static Collection<ILaborProvider> getAllProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }
}