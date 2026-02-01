package de.tebrox.vertexCore.command.internal.resolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ResolverRegistry {

    private final Map<Class<?>, ArgumentResolver<?>> resolvers = new ConcurrentHashMap<>();

    public <T> void register(ArgumentResolver<T> resolver) {
        resolvers.put(resolver.type(), resolver);
    }

    @SuppressWarnings("unchecked")
    public <T> ArgumentResolver<T> resolverFor(Class<T> type) {
        return (ArgumentResolver<T>) resolvers.get(type);
    }
}
