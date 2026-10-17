package com.ael.algoryqrservice.qrservice.factory;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.ael.algoryqrservice.qrservice.qr.dto.QrRequest;
import com.ael.algoryqrservice.qrservice.provider.QrProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class QrProviderFactory {
    private final Map<Type, QrProvider<?>> providers = new EnumMap<>(Type.class);

    public QrProviderFactory(List<QrProvider<?>> qrProviders){
        for (QrProvider<?> provider : qrProviders) {
            providers.put(provider.getType(), provider);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends QrRequest> QrProvider<T> get(Type type, Class<T> requestType) {
        QrProvider<?> provider = providers.get(type);

        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for type: " + type);
        }

        QrProvider<T> typedProvider = (QrProvider<T>) provider;

        if (!typedProvider.requestType().equals(requestType)) {
            throw new IllegalArgumentException(
                    "Request type mismatch for " + type +
                            ". Expected: " + typedProvider.requestType().getSimpleName() +
                            ", Actual: " + requestType.getSimpleName()
            );
        }

        return typedProvider;
    }


}
