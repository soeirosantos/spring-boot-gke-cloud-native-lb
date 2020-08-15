package com.todevornot.videos.rest

import com.todevornot.videos.domain.Video
import com.todevornot.videos.domain.Videos
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/videos")
class VideoController(val videos: Videos) {

    @GetMapping
    fun list() = videos.all()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Int): Video = videos.get(id) ?: throw NotFound()

}

@ResponseStatus(code = HttpStatus.NOT_FOUND, reason = "Video Not Found")
class NotFound : RuntimeException()
