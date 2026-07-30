package com.app.common.config

import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {

    @Test
    fun support_url_points_to_readme_support_section() {
        assertEquals(
            "https://github.com/yet300/BlockBlast#support-me",
            AppConfig.GITHUB_SUPPORT_URL,
        )
    }
}
