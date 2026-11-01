package com.volleyball.game.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Russian interface: exact texts, capitals, joined texts and templates. */
class LangTest {
    @AfterEach
    void english() {
        Lang.set(Lang.Language.EN);
    }

    @Test
    void textsAreTranslatedWithTheProperTerms() {
        Lang.set(Lang.Language.RU);
        assertEquals("Быстрый матч", Lang.t("Quick match"));
        assertEquals("ПРИЁМ ПОДАЧИ", Lang.t("SERVE RECEIVE"));
        assertEquals("Подача  ·  Блок", Lang.t("Serve  ·  Block"));
        assertEquals("Главный тренер: Igor Petrov", Lang.t("Head coach: Igor Petrov"));
        assertEquals("№4 Kozlov вместо №11 Kuznetsov   (1 / 6)", Lang.t("#4 Kozlov in for #11 Kuznetsov   (1 / 6)"));
        assertEquals("использовано 0 из 6 в этой партии", Lang.t("0 / 6 used this set"));
        assertEquals("Скидка", Lang.t("Tip"));
        assertEquals("Задержка мяча", Lang.t("Held / thrown ball"));
        assertEquals("Igor Petrov", Lang.t("Igor Petrov"), "names stay");
        assertEquals("ПОВОРОТ К МЫШИ: ВКЛ", Lang.t("FACE THE MOUSE: ON"), "a template in capitals");
        assertEquals("Подача не выполнена вовремя (№7 Volkov)", Lang.t("Serve not made within the time limit (#7 Volkov)"));
        assertEquals("‹  До трёх побед, официальный формат  ›", Lang.t("‹  Best of five - official  ›"));
        assertEquals("Последнее очко: Аут  ·  очко гостям (№7 Volkov)  ·  смена подачи, переход",
                Lang.t("Last point: Ball out  ·  point away (#7 Volkov)  ·  side-out, rotation"));
        Lang.set(Lang.Language.EN);
        assertEquals("Quick match", Lang.t("Quick match"));
    }
}
