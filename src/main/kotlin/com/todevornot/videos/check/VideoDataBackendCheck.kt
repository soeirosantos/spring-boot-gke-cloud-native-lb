package com.todevornot.videos.check

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

/**
 * This HealthIndicator simulates a health check on a
 * data backend. It could, for example, be checking
 * the health of a Redis or MongoDB instance.
 */
@Component
class VideoDataBackendCheck : HealthIndicator {

    private var isHealthy = true

    override fun health(): Health {
        return if (isHealthy) Health.up().build() else Health.down().build()
    }

    fun down() {
        isHealthy = false
    }
}
