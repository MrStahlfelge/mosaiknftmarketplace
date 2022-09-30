package com.example.ergomosaik.mosaikapp

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@SpringBootApplication
class MosaikappApplication {
	@Bean
	@Primary
	fun objectMapper(): ObjectMapper {
		// enables controller methods annotated with @ResponseBody to directly return
		// Mosaik Actions and elements that will get serialized by Spring automatically
		return org.ergoplatform.mosaik.jackson.MosaikSerializer.getMosaikMapper()
	}
}

fun main(args: Array<String>) {
	runApplication<MosaikappApplication>(*args)
}

const val mosaikAppVersion = 1

fun formatErgAmount(rawAmount: Long): String =
	rawAmount.toBigDecimal().movePointLeft(9).toPlainString().trimEnd('0').trimEnd('.')
