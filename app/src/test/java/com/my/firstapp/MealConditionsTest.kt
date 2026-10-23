package com.my.firstapp

import org.junit.Assert.*
import org.junit.Test

class MealConditionsTest {
    @Test fun draftsDoNotMutateOriginalSettings() {
        val original=Settings(budget=20.0,maxWalkMinutes=10)
        val edited=MealConditions.from(original).copy(budget=30.0,walk=15)
        assertEquals(20.0,original.budget!!,0.0)
        assertEquals(10,original.maxWalkMinutes)
        assertEquals(30.0,edited.budget!!,0.0)
    }
    @Test fun applyingOnlyChangesDecisionFields() {
        val original=Settings(nickname="测试",theme="mint",sound=false,lastAutoBackupDay="2026-10-19")
        val applied=MealConditions(30.0,15,3,false,false).applyTo(original)
        assertEquals(original.nickname,applied.nickname)
        assertEquals(original.theme,applied.theme)
        assertEquals(original.sound,applied.sound)
        assertEquals(original.lastAutoBackupDay,applied.lastAutoBackupDay)
        assertEquals(MealConditions(30.0,15,3,false,false),MealConditions.from(applied))
    }
    @Test fun unchangedConditionsAreEqualRegardlessOfTheme() {
        assertEquals(MealConditions.from(Settings()),MealConditions.from(Settings(theme="mint",nickname="someone")))
    }
}
