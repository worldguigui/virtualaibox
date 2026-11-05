package com.own.virtualaibox.domain.world;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TownQuest {
    private String id;
    private String title;
    private String description;
    private String assigneeId;
    private int reward;
    private String status;

    public static TownQuest open(String title, String description, int reward) {
        return new TownQuest(UUID.randomUUID().toString(), title, description, null, reward, "open");
    }
}
