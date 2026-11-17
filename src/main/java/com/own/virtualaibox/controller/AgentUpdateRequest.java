package com.own.virtualaibox.controller;

import com.own.virtualaibox.domain.agent.PersonalityProfile;

// 修改已存在居民的更新参数。
public record AgentUpdateRequest(String name, Integer x, Integer y, PersonalityProfile personality) {
    public AgentUpdateRequest(String name, Integer x, Integer y) {
        this(name, x, y, null);
    }
}
