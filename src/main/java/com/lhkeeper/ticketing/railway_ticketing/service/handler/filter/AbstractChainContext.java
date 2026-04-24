package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽象责任链上下文
 */
@Component
public final class AbstractChainContext<T> implements CommandLineRunner {

    private final Map<String, List<AbstractChainHandler>> abstractChainHandlerContainer = new HashMap<>();

    private final ApplicationContext applicationContext;

    public AbstractChainContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void handler(String mark, T requestParam) {
        List<AbstractChainHandler> chainHandlers = abstractChainHandlerContainer.get(mark);
        if (CollectionUtils.isEmpty(chainHandlers)) {
            throw new RuntimeException(String.format("[%s] Chain of Responsibility ID is undefined.", mark));
        }
        chainHandlers.forEach(each -> each.handler(requestParam));
    }

    @Override
    public final void run(final String... args) {
        Map<String, AbstractChainHandler> chainFilterMap = applicationContext
                .getBeansOfType(AbstractChainHandler.class);
        chainFilterMap.forEach((beanName, chainHandler) -> {
            List<AbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(chainHandler.mark());
            if (abstractChainHandlers == null) {
                abstractChainHandlers = new ArrayList<>();
            }
            abstractChainHandlers.add(chainHandler);
            List<AbstractChainHandler> actualAbstractChainHandlers = abstractChainHandlers.stream()
                    .sorted(Comparator.comparing(Ordered::getOrder))
                    .collect(Collectors.toList());
            abstractChainHandlerContainer.put(chainHandler.mark(), actualAbstractChainHandlers);
        });
    }
}
