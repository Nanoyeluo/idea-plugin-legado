package com.github.sunrishe.legado_idea.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegadoSettingsStateTest {

    @Test
    void defaultValuesAreSet() {
        LegadoSettingsState state = new LegadoSettingsState();
        assertEquals("http://127.0.0.1:1122", state.webServeUrl);
        assertEquals("阅读", state.panelTitle);
    }
}
