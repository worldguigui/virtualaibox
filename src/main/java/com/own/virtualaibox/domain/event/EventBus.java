package com.own.virtualaibox.domain.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.own.virtualaibox.properties.RuntimeProperties;

import java.util.*;
import java.util.concurrent.*;

/**
 * 事件总线
 * 中央事件分发中心，支持：
 * - 多种事件类型的订阅/发布
 * - 事件优先级处理
 * - 同步/异步执行
 * - 事件历史记录（环形缓冲上限来自 virtual-ai-box.runtime.event-history-size）
 */
@Component
@Slf4j
public class EventBus {

    // 事件类型 -> 监听者列表
    private final Map<String, List<EventListener>> subscribers = new ConcurrentHashMap<>();

    // 全局监听者（监听所有事件）
    private final List<EventListener> globalListeners = new CopyOnWriteArrayList<>();

    // 事件历史记录
    private final Deque<DomainEvent> eventHistory = new ConcurrentLinkedDeque<>();

    // 历史记录最大容量
    private final int maxHistorySize;

    // 异步执行线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    /** 测试/纯代码路径：使用默认历史上限。 */
    public EventBus() {
        this(RuntimeProperties.defaults());
    }

    /** Spring 装配：历史上限来自运行时配置。 */
    @Autowired
    public EventBus(RuntimeProperties runtime) {
        this.maxHistorySize = runtime.getEventHistorySize();
    }

    /**
     * 订阅特定事件类型
     * @param eventType 事件类型
     * @param listener 事件监听者
     */
    public void subscribe(String eventType, EventListener listener) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(listener);
        log.debug("EventBus: Subscribed to event type: {}", eventType);
    }
    
    /**
     * 订阅所有事件
     * @param listener 事件监听者
     */
    public void subscribeGlobal(EventListener listener) {
        globalListeners.add(listener);
        log.debug("EventBus: Global listener registered");
    }
    
    /**
     * 取消订阅特定事件类型
     * @param eventType 事件类型
     * @param listener 事件监听者
     */
    public void unsubscribe(String eventType, EventListener listener) {
        List<EventListener> listeners = subscribers.get(eventType);
        if (listeners != null) {
            listeners.remove(listener);
            log.debug("EventBus: Unsubscribed from event type: {}", eventType);
        }
    }
    
    /**
     * 取消全局订阅
     * @param listener 事件监听者
     */
    public void unsubscribeGlobal(EventListener listener) {
        globalListeners.remove(listener);
        log.debug("EventBus: Global listener unregistered");
    }
    
    /**
     * 同步发布事件
     * @param event 待发布的事件
     */
    public void publish(DomainEvent event) {
        publishInternal(event, false);
    }
    
    /**
     * 异步发布事件
     * @param event 待发布的事件
     */
    public void publishAsync(DomainEvent event) {
        publishInternal(event, true);
    }
    
    /**
     * 批量发布事件
     * @param events 事件列表
     */
    public void publishBatch(List<DomainEvent> events) {
        // 按优先级排序（高优先级先发布）
        events.stream()
                .sorted(Comparator.comparingInt(DomainEvent::getPriority).reversed())
                .forEach(this::publish);
        log.info("EventBus: Published batch of {} events", events.size());
    }
    
    /**
     * 批量异步发布事件
     * @param events 事件列表
     */
    public void publishBatchAsync(List<DomainEvent> events) {
        events.stream()
                .sorted(Comparator.comparingInt(DomainEvent::getPriority).reversed())
                .forEach(this::publishAsync);
    }
    
    /**
     * 内部发布逻辑
     */
    private void publishInternal(DomainEvent event, boolean async) {
        if (event == null) {
            log.warn("EventBus: Attempting to publish null event");
            return;
        }
        
        // 记录事件到历史
        recordEvent(event);
        
        // 获取监听者列表
        List<EventListener> listeners = new ArrayList<>(globalListeners);
        List<EventListener> typeListeners = subscribers.getOrDefault(event.getEventType(), Collections.emptyList());
        listeners.addAll(typeListeners);
        
        
        if (listeners.isEmpty()) {
            log.warn("EventBus: No listeners for event type: {}", event.getEventType());
            return;
        }
        
        log.info("EventBus: Publishing event - type: {}, description: {}, listeners: {}", 
                event.getEventType(), event.getDescription(), listeners.size());
        
        if (async) {
            for (EventListener listener : listeners) {
                executorService.submit(() -> {
                    try {
                        listener.onEvent(event);
                    } catch (Exception e) {
                        log.error("EventBus: Error in async event listener", e);
                    }
                });
            }
        } else {
            for (EventListener listener : listeners) {
                try {
                    listener.onEvent(event);
                } catch (Exception e) {
                    log.error("EventBus: Error in event listener", e);
                }
            }
        }
        
        event.setProcessed(true);
    }
    
    /**
     * 记录事件到历史
     */
    @Deprecated
    private void recordEvent(DomainEvent event) {
        eventHistory.offerLast(event);
        // 保持历史大小限制
        while (eventHistory.size() > maxHistorySize) {
            eventHistory.removeFirst();
        }
    }
    
    /**
     * 获取事件历史
     * @param limit 返回数量限制
     * @return 最近的事件列表
     */
    public List<DomainEvent> getEventHistory(int limit) {
        return eventHistory.stream()
                .limit(limit)
                .toList();
    }
    
    /**
     * 清空历史
     */
    public void clearHistory() {
        eventHistory.clear();
        log.info("EventBus: Event history cleared");
    }
    
    /**
     * 获取订阅者数量
     */
    public int getSubscriberCount() {
        return globalListeners.size() + subscribers.values().stream().mapToInt(List::size).sum();
    }
}
