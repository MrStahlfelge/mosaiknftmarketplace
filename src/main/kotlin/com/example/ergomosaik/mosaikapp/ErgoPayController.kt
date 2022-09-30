package com.example.ergomosaik.mosaikapp

import com.example.ergomosaik.mosaikapp.api.SkyHarborService
import org.ergoplatform.appkit.InputBoxesSelectionException
import org.ergoplatform.ergopay.ErgoPayResponse
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@CrossOrigin
class ErgoPayController(
    private val skyHarborService: SkyHarborService,
) {

    @GetMapping("/purchase/{saleId}/{buyer}")
    fun purchaseSale(
        @PathVariable saleId: Int,
        @PathVariable buyer: String,
    ): ErgoPayResponse {
        val response = ErgoPayResponse()

        try {
            val (message, reduced) = skyHarborService.buildPurchaseTransaction(saleId, buyer)
            response.reducedTx = Base64.getUrlEncoder().encodeToString(reduced)
            response.address = buyer
            response.message = "Please sign the transaction to complete with the purchase.\n\n$message"
            response.messageSeverity = ErgoPayResponse.Severity.INFORMATION
        } catch (nee: InputBoxesSelectionException.NotEnoughErgsException) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR
            response.message = "You don't have enough ERG on the chosen address to make the purchase."
        } catch (t: Throwable) {
            response.messageSeverity = ErgoPayResponse.Severity.ERROR
            response.message = t.message
        }
        return response
    }

}