package com.own.virtualaibox.domain.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 保存 Agent 的位置和名称状态。
public class AgentState {
    private int x;
    private int y;
    private String name;
}
