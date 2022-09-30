package com.example.ergomosaik.mosaikapp

import com.example.ergomosaik.mosaikapp.api.SkyHarborService
import com.example.ergomosaik.mosaikapp.ergo.PeerService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MosaikappApplicationTests {

	@Test
	fun contextLoads() {
	}

	@Test
	fun checkSkyHarborBuy() {
		val skyHarborService = SkyHarborService(PeerService())

		// without royalty
		skyHarborService.buildPurchaseTransaction(22518, "9g8gaARC3N8j9v97wmnFkhDMxHHFh9PEzVUtL51FGSNwTbYEnnk")
		// with royalty
		skyHarborService.buildPurchaseTransaction(22517, "9g8gaARC3N8j9v97wmnFkhDMxHHFh9PEzVUtL51FGSNwTbYEnnk")
	}
}
