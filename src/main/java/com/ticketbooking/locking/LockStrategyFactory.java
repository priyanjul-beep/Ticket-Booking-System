package com.ticketbooking.locking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LockStrategyFactory {

    private final List<SeatLockStrategy> strategies;
    private final NoOpSeatLockStrategy noOpSeatLockStrategy;

    public SeatLockStrategy getStrategy(LockStrategyType type) {
        if (type == null) {
            type = LockStrategyType.REDIS;
        }

        if (type == LockStrategyType.PESSIMISTIC || type == LockStrategyType.OPTIMISTIC || type == LockStrategyType.NO_LOCK) {
            return noOpSeatLockStrategy;
        }

        final LockStrategyType targetType = type;
        return strategies.stream()
                .filter(s -> s.getType() == targetType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported lock strategy: " + targetType));
    }
}
