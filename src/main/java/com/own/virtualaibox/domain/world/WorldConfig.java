package com.own.virtualaibox.domain.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.own.virtualaibox.domain.agent.PersonalityProfile;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 世界运行配置：尺寸、默认居民（含人格）与初始化参数。
 *
 * <p>配置主源是 application.yml 的 {@code virtual-ai-box.world} 段；
 * 下面字段初始化器里的默认值是 yml 缺失时的代码兜底，两者应保持一致
 * （网格尺寸、默认居民坐标与人格）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "virtual-ai-box.world")
public class WorldConfig {
    private String name = "VirtualAIBox Town";
    private int width = 37;
    private int height = 37;
    private Map<String, Boolean> rules = new LinkedHashMap<>(Map.of(
            "movement", true,
            "socialInteraction", true,
            "itemSpawning", true,
            "combat", false,
            "trading", false));
    private List<WorldItemDefinition> items = List.of();
    private List<DefaultResident> defaultResidents = List.of(
            new DefaultResident("Alice", 6, 2, new PersonalityProfile(
                    "探索者",
                    "好奇、谨慎",
                    "温和，喜欢先观察再表达",
                    "了解小镇并寻找新地点",
                    "重视事实和互相帮助",
                    "只知道已经感知到或记忆中的世界信息")),
            new DefaultResident("Bob", 18, 17, new PersonalityProfile(
                    "社区居民",
                    "热情、主动",
                    "直接、友好，愿意发起对话",
                    "认识其他居民并建立联系",
                    "重视承诺和社区关系",
                    "只知道已经感知到或记忆中的世界信息")));

    /** 代码兜底默认配置（无参构造的字段初始化器是唯一默认值来源）。 */
    public static WorldConfig defaultConfig() {
        return new WorldConfig();
    }

    public void validate() {
        if (width < 1 || width > 1000 || height < 1 || height > 1000) {
            throw new IllegalArgumentException("world dimensions must be between 1 and 1000");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("world name must not be blank");
        }
        if (rules == null) {
            rules = new LinkedHashMap<>();
        }
        if (items == null) {
            items = List.of();
        }
        items.forEach(item -> {
            if (item == null || item.getId() == null || item.getId().isBlank()
                    || item.getName() == null || item.getName().isBlank()) {
                throw new IllegalArgumentException("world items require non-blank id and name");
            }
            if (item.getMaxCount() < 0 || item.getSpawnWeight() < 0) {
                throw new IllegalArgumentException("world item counts and weights must not be negative");
            }
        });
        if (defaultResidents == null) {
            defaultResidents = List.of();
        }
        defaultResidents.forEach(resident -> {
            if (resident == null || resident.getName() == null || resident.getName().isBlank()) {
                throw new IllegalArgumentException("default residents require non-blank name");
            }
        });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefaultResident {
        private String name;
        private int x;
        private int y;
        /** 该居民初始化时的人格；null 时由世界引擎回落到配置的通用兜底人格。 */
        private PersonalityProfile personality;

        public DefaultResident(String name, int x, int y) {
            this(name, x, y, null);
        }
    }
}
