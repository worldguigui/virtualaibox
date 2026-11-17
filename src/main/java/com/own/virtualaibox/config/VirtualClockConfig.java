package com.own.virtualaibox.config;

import com.own.virtualaibox.properties.VirtualClockProperties;
import lombok.Data;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

@Data
@Configuration
@EnableConfigurationProperties(VirtualClockProperties.class)
// 从配置属性装配虚拟时钟运行参数。
public class VirtualClockConfig {

    private final int tick;
    private final Instant metaInstant;
    private final int interval;

    // 使用虚拟时钟配置属性初始化运行参数。
    /**
     * @param properties 虚拟时钟配置属性
     */
    public VirtualClockConfig(VirtualClockProperties properties) {
        this.tick = properties.getTick();
        this.metaInstant = properties.getMetaInstant();
        this.interval = properties.getInterval();
    }


}
