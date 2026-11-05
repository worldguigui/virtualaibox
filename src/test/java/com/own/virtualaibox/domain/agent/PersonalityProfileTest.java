package com.own.virtualaibox.domain.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class PersonalityProfileTest {

    @Test
    void genericProfileIsStable() {
        PersonalityProfile first = PersonalityProfile.generic();
        PersonalityProfile second = PersonalityProfile.generic();

        assertEquals(first, second);
        assertEquals("居民", first.getRole());
    }

    @Test
    void nullProfileFallsBackToProvidedFallback() {
        PersonalityProfile fallback = new PersonalityProfile("守林人", "沉稳", "低声", "守护边界", "重视秩序", "只知道林中事");

        assertEquals(fallback, PersonalityProfile.normalizeFor(null, fallback));
        assertEquals(PersonalityProfile.generic(), PersonalityProfile.normalizeFor(null, null));
    }

    @Test
    void blankFieldsFallBackToConfiguredProfile() {
        PersonalityProfile fallback = new PersonalityProfile("守林人", "沉稳", "低声", "守护边界", "重视秩序", "只知道林中事");
        PersonalityProfile partial = new PersonalityProfile("旅人", "", null, "四处游历", "  ", "只知道走过的路");

        PersonalityProfile merged = PersonalityProfile.normalizeFor(partial, fallback);

        assertEquals("旅人", merged.getRole());
        assertEquals("沉稳", merged.getTemperament());
        assertEquals("低声", merged.getSpeakingStyle());
        assertEquals("四处游历", merged.getMotivations());
        assertEquals("重视秩序", merged.getValues());
        assertEquals("只知道走过的路", merged.getKnowledgeBoundary());
    }

    @Test
    void distinctProfilesAreNotEqual() {
        assertNotEquals(PersonalityProfile.generic(),
                new PersonalityProfile("旅人", "沉稳", "低声", "守护边界", "重视秩序", "只知道林中事"));
        assertSame(PersonalityProfile.generic().getClass(), PersonalityProfile.class);
    }

    @Test
    void promptContextContainsAllPersonalityFields() {
        PersonalityProfile profile = new PersonalityProfile(
                "探索者", "好奇", "温和", "探索小镇", "互助", "已感知或记忆中的信息");

        String context = profile.toPromptContext();

        assertTrue(context.contains("身份：探索者"));
        assertTrue(context.contains("性情：好奇"));
        assertTrue(context.contains("表达方式：温和"));
        assertTrue(context.contains("当前动机：探索小镇"));
        assertTrue(context.contains("重视的价值：互助"));
        assertTrue(context.contains("知识边界：已感知或记忆中的信息"));
    }
}
