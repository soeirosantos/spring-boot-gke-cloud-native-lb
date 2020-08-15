package com.todevornot.videos

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@SpringBootApplication
class VideosApplication

fun main(args: Array<String>) {
	runApplication<VideosApplication>(*args)
}

@Component
class InitCheck {

	val logger: Logger = LoggerFactory.getLogger(InitCheck::class.java)

	@EventListener
	fun checkApplicationStarted(startedEvent: ApplicationStartedEvent) {
		logger.info("started...")
	}
}
