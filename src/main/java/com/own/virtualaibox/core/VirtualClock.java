package com.own.virtualaibox.core;

import com.own.virtualaibox.config.VirtualClockConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
// 管理虚拟世界的时间刻度并换算当前虚拟时间。
public class VirtualClock {

    private final VirtualClockConfig clockConfig;

    // 虚拟时间的元时间
    private Instant metaInstant;
    // 当前时间刻度
    private int tick;
    // 当前时间步长
    // 时间步长由配置文件决定，不对外提供修改接口
    // 单位是秒
    // 跟现实世界的时间流速。
    private int interval;

    // 构造器注入
    // 使用配置初始化虚拟时钟。
    /**
     * @param clockConfig 虚拟时钟运行配置
     */
    public VirtualClock(VirtualClockConfig clockConfig) {
        this.clockConfig = clockConfig;
        this.metaInstant = clockConfig.getMetaInstant();
        log.info("VirtualClock initialized: " + metaInstant);
        this.tick = clockConfig.getTick();
        this.interval = clockConfig.getInterval();
    }

    // 将虚拟时间向前推进一个刻度。
    public void stepForward() {
        this.tick++;
        log.info("VirtualClock tick: " + tick);
    }

    // 将虚拟时间向后回退一个刻度。
    public void stepBackward() {
        this.tick--;
        log.info("VirtualClock tick: " + tick);
    }


    // 根据元时间、时间步长和当前刻度计算虚拟时间。
    /**
     * @return 当前虚拟时间
     */
    public Instant getCurrentTime() {
        log.info("getCurrentTime: " + interval * tick);
        return metaInstant.plusSeconds(interval * tick);
    }

    // 获取当前虚拟时间刻度。
    /**
     * @return 当前刻度
     */
    public int getTick() {
        return tick;
    }

}
