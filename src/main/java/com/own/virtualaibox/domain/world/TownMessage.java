package com.own.virtualaibox.domain.world;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TownMessage {
    private String id;
    private String fromAgentId;
    private String toAgentId;
    private String content;
    private int tick;
    private Instant createdAt;

    public static TownMessage create(String fromAgentId, String toAgentId, String content, int tick) {
        return new TownMessage(UUID.randomUUID().toString(), fromAgentId, toAgentId, content, tick, Instant.now());
    }
}
