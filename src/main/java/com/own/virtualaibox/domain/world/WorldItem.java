package com.own.virtualaibox.domain.world;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorldItem {
    private String id;
    private String definitionId;
    private int x;
    private int y;
    private String ownerId;
    private int quantity = 1;

    public static WorldItem loose(String definitionId, int x, int y) {
        return new WorldItem(UUID.randomUUID().toString(), definitionId, x, y, null, 1);
    }
}
