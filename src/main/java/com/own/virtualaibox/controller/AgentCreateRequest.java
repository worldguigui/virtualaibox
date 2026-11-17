package com.own.virtualaibox.controller;

import com.own.virtualaibox.domain.agent.PersonalityProfile;

// 新居民创建请求的输入参数。
public record AgentCreateRequest(String name, Integer x, Integer y, PersonalityProfile personality) {
    public AgentCreateRequest(String name, Integer x, Integer y) {
        this(name, x, y, null);
    }
}
