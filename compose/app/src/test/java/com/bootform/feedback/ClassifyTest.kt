package com.bootform.feedback

import com.bootform.feedback.BootFormFeedback.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifyTest {

    private fun classify(status: Int, code: String? = null, hadScreenshot: Boolean = false) =
        BootFormFeedback.classify(status, code, hadScreenshot)

    @Test fun accepted_is_sent() = assertEquals(Outcome.Sent, classify(200))

    @Test fun free_plan_refusing_the_file_drops_only_the_screenshot() =
        assertEquals(Outcome.DropScreenshot, classify(403, "PLAN_LIMIT", hadScreenshot = true))

    @Test fun unclaimed_form_refusing_the_file_drops_only_the_screenshot() =
        assertEquals(Outcome.DropScreenshot, classify(422, "FORM_NOT_CLAIMED", hadScreenshot = true))

    @Test fun oversized_file_drops_only_the_screenshot() =
        assertEquals(Outcome.DropScreenshot, classify(422, "FILE_TOO_LARGE", hadScreenshot = true))

    @Test fun plan_limit_without_a_screenshot_is_not_retried_forever() =
        assertEquals(Outcome.Rejected, classify(403, "PLAN_LIMIT", hadScreenshot = false))

    @Test fun rate_limit_is_retried() = assertEquals(Outcome.RetryLater, classify(429, "RATE_LIMITED"))

    @Test fun server_error_is_retried() = assertEquals(Outcome.RetryLater, classify(503))

    @Test fun deleted_form_is_given_up() = assertEquals(Outcome.Rejected, classify(410, "FORM_DELETED"))

    @Test fun disabled_form_is_given_up() = assertEquals(Outcome.Rejected, classify(403, "FORM_DISABLED"))

    @Test fun invalid_form_id_is_given_up() = assertEquals(Outcome.Rejected, classify(422, "INVALID_FORM_ID"))
}
