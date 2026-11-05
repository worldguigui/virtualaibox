package com.own.virtualaibox.domain.world;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialRelation {
    private String agentId;
    private String otherAgentId;
    private int trust;
    private int affinity;
    private int interactions;
    private String status;
}
