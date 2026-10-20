package com.dispatch.delivery;

import com.dispatch.entity.enums.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry holding all available DeliveryStrategy implementations.
 */
@Component
public class DeliveryStrategyRegistry {

    private final Map<NotificationType, DeliveryStrategy> strategyMap;

    public DeliveryStrategyRegistry(List<DeliveryStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(DeliveryStrategy::supportedType, Function.identity()));
    }

    public DeliveryStrategy getStrategy(NotificationType type) {
        DeliveryStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("No delivery strategy configured for type: " + type);
        }
        return strategy;
    }
}
