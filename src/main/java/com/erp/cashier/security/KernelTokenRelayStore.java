package com.erp.cashier.security;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class KernelTokenRelayStore {
    private final Map<String, String> tokens = new ConcurrentHashMap<>();

    public void store(String facadeToken, String kernelToken) {
        if (StringUtils.hasText(facadeToken) && StringUtils.hasText(kernelToken)) {
            tokens.put(facadeToken, kernelToken);
        }
    }

    public Optional<String> resolve(String facadeToken) {
        return StringUtils.hasText(facadeToken)
                ? Optional.ofNullable(tokens.get(facadeToken))
                : Optional.empty();
    }
}
