package com.todevornot.videos.domain

data class Video(
        val title: String,
        val description: String,
        val url: String,
        val tags: List<String>
)
