package com.own.virtualaibox.domain.world;

import java.util.ArrayList;
import java.util.List;

import com.own.virtualaibox.domain.agent.Agent;

import lombok.Data;

@Data
/** 保存世界边界及其中的 Agent 集合。 */
public class World {
    private int width;
    private int height;
    private String name;
    private String worldDescription;
    private java.util.Map<String, Boolean> rules;
    private List<WorldItemDefinition> items;
    private List<Agent> agents = new ArrayList<>();
    private List<WorldItem> itemInstances = new ArrayList<>();
    private List<SocialRelation> relationships = new ArrayList<>();
    private List<TownMessage> messages = new ArrayList<>();
    private List<TownQuest> quests = new ArrayList<>();

    public World() {
        this(WorldConfig.defaultConfig());
    }

    public World(int width, int height) {
        this.width = width;
        this.height = height;
        this.name = "VirtualAIBox Town";
        this.worldDescription = "一个居民会探索、交流、收集资源并完成任务的小镇。";
        this.rules = new java.util.LinkedHashMap<>();
        this.items = new ArrayList<>();
    }

    public World(WorldConfig config) {
        config = config == null ? WorldConfig.defaultConfig() : config;
        config.validate();
        this.width = config.getWidth();
        this.height = config.getHeight();
        this.name = config.getName();
        this.worldDescription = config.getWorldDescription();
        this.rules = new java.util.LinkedHashMap<>(config.getRules());
        this.items = new ArrayList<>(config.getItems());
    }

    /** 应用配置映射；不会清理居民、资源实例、关系、消息或任务。 */
    public synchronized void applyConfig(WorldConfig config) {
        config.validate();
        if (agents.stream().anyMatch(agent -> agent.getState().getX() >= config.getWidth()
                || agent.getState().getY() >= config.getHeight())) {
            throw new IllegalArgumentException("new world dimensions would place an agent outside the world");
        }
        this.width = config.getWidth();
        this.height = config.getHeight();
        this.name = config.getName();
        this.worldDescription = config.getWorldDescription();
        this.rules = new java.util.LinkedHashMap<>(config.getRules());
        this.items = new ArrayList<>(config.getItems());
    }

    /** 将已完成身份和位置校验的 Agent 加入世界。 */
    /**
     * @param agent 待加入世界的 Agent
     */
    public void addAgent(Agent agent) {
        agents.add(agent);
    }

    public List<Agent> getAgents() { return agents; }
    public String getWorldDescription() { return worldDescription; }
    public java.util.Map<String, Boolean> getRules() { return rules; }
    public List<WorldItemDefinition> getItems() { return items; }
    public List<WorldItem> getItemInstances() { return itemInstances; }
    public List<SocialRelation> getRelationships() { return relationships; }
    public List<TownMessage> getMessages() { return messages; }
    public List<TownQuest> getQuests() { return quests; }
}
