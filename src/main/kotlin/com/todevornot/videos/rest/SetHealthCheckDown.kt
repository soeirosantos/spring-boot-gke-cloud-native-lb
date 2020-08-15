package com.todevornot.videos.rest

import com.todevornot.videos.check.VideoDataBackendCheck
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * This is an utility to force a health check issue in
 * the application
 */
@RestController
class SetHealthCheckDown(val check: VideoDataBackendCheck) {

    @GetMapping("/down")
    fun setDown() {
        check.down()
    }
}
