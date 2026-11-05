package com.own.virtualaibox.domain.event.events;

import com.own.virtualaibox.domain.event.DomainEvent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class TownActionEvent extends DomainEvent {
    private String action;
    private String actorId;
    private String targetId;
    private String details;

    @Override
    public String getEventType() {
        return "town.action";
    }

    @Override
    public String getDescription() {
        return action + (details == null || details.isBlank() ? "" : ": " + details);
    }
}
