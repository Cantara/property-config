package no.cantara.config;

import java.util.ServiceLoader;

public class ProviderLoader {

    public static <R, F extends ProviderFactory<R>> F factoryOf(String providerIdOrClassname, Class<F> abstractFactoryClazz) {
        ServiceLoader<F> loader = ServiceLoader.load(abstractFactoryClazz);
        for (F factory : loader) {
            String providerId = factory.alias();
            Class<?> providerClass = factory.providerClass();
            Class<? extends ProviderFactory> concreteFactoryClass = factory.getClass();
            if (providerIdOrClassname.equals(providerId)
                    || providerIdOrClassname.equals(concreteFactoryClass.getName())
                    || providerIdOrClassname.equals(concreteFactoryClass.getSimpleName())
                    || providerIdOrClassname.equals(providerClass.getName())
                    || providerIdOrClassname.equals(providerClass.getSimpleName())
            ) {
                return factory;
            }
        }
        throw new RuntimeException("No " + abstractFactoryClazz.getSimpleName() + " provider found for providerIdOrClassname: " + providerIdOrClassname);
    }

    public static <R, T extends ProviderFactory<R>> R configure(ApplicationProperties applicationProperties, String providerIdOrClassname, Class<T> clazz) {
        ServiceLoader<T> loader = ServiceLoader.load(clazz);
        for (T factory : loader) {
            String providerId = factory.alias();
            Class<?> providerClass = factory.providerClass();
            Class<? extends ProviderFactory> concreteFactoryClass = factory.getClass();
            if (providerIdOrClassname.equals(providerId)
                    || providerIdOrClassname.equals(concreteFactoryClass.getName())
                    || providerIdOrClassname.equals(concreteFactoryClass.getSimpleName())
                    || providerIdOrClassname.equals(providerClass.getName())
                    || providerIdOrClassname.equals(providerClass.getSimpleName())
            ) {
                return factory.create(applicationProperties);
            }
        }
        throw new RuntimeException("No " + clazz.getSimpleName() + " provider found for providerIdOrClassname: " + providerIdOrClassname);
    }
}
