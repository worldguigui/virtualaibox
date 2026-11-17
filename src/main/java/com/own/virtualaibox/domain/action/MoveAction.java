package com.own.virtualaibox.domain.action;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 描述 Agent 在世界中的相对移动动作。
public class MoveAction {
    private String agentId;
    private int deltaX;
    private int deltaY;
    private String reason;
}
