package com.lanu.globaldonuksatisradari.crm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrmStageRulesTest {
    @Test
    fun prospect_can_enter_field_work_but_not_jump_to_order() {
        assertTrue(CrmStageRules.canTransition(CrmStage.PROSPECT, CrmStage.VISIT))
        assertTrue(CrmStageRules.canTransition(CrmStage.PROSPECT, CrmStage.MEETING))
        assertFalse(CrmStageRules.canTransition(CrmStage.PROSPECT, CrmStage.ORDER))
    }

    @Test
    fun proposal_and_sample_can_reach_order() {
        assertTrue(CrmStageRules.canTransition(CrmStage.PROPOSAL, CrmStage.ORDER))
        assertTrue(CrmStageRules.canTransition(CrmStage.SAMPLE, CrmStage.ORDER))
        assertTrue(CrmStageRules.canTransition(CrmStage.ORDER, CrmStage.ACTIVE_CUSTOMER))
    }

    @Test
    fun lost_customer_can_be_reactivated_as_prospect_only() {
        assertTrue(CrmStageRules.canTransition(CrmStage.LOST, CrmStage.PROSPECT))
        assertFalse(CrmStageRules.canTransition(CrmStage.LOST, CrmStage.ORDER))
    }
}
