package com.todevornot.videos.domain

import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/**
 * Simulates data access to some data backend
 */
@Component
class Videos {

    private val videos = hashMapOf<Int, Video>()

    @PostConstruct
    fun init() {
        for (i in 1..10) {
            videos[i] = Video(
                    "My favorite video $i",
                    "Description of my video",
                    "https://my-videos-catalog.com/$i",
                    listOf("programming", "favorites")
            )
        }
    }

    fun all(): List<Video> {
        return ArrayList<Video>(videos.values)
    }

    fun get(id: Int): Video? {
        return videos[id]
    }
}
