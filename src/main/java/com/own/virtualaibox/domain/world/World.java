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
    private java.util.Map<String, Boolean> rules;
    private List<WorldItemDefinition> items;
    private List<Agent> agents = new ArrayList<>();

    public World() {
        this(WorldConfig.defaultConfig());
    }

    public World(int width, int height) {
        this.width = width;
        this.height = height;
        this.name = "VirtualAIBox Town";
        this.rules = new java.util.LinkedHashMap<>();
        this.items = new ArrayList<>();
    }

    public World(WorldConfig config) {
        config = config == null ? WorldConfig.defaultConfig() : config;
        config.validate();
        this.width = config.getWidth();
        this.height = config.getHeight();
        this.name = config.getName();
        this.rules = new java.util.LinkedHashMap<>(config.getRules());
        this.items = new ArrayList<>(config.getItems());
    }

    public synchronized void applyConfig(WorldConfig config) {
        config.validate();
        if (agents.stream().anyMatch(agent -> agent.getState().getX() >= config.getWidth()
                || agent.getState().getY() >= config.getHeight())) {
            throw new IllegalArgumentException("new world dimensions would place an agent outside the world");
        }
        this.width = config.getWidth();
        this.height = config.getHeight();
        this.name = config.getName();
        this.rules = new java.util.LinkedHashMap<>(config.getRules());
        this.items = new ArrayList<>(config.getItems());
    }

    /** 将 Agent 加入世界。 */
    /**
     * @param agent 待加入世界的 Agent
     */
    public void addAgent(Agent agent) {
        agents.add(agent);
    }
}
