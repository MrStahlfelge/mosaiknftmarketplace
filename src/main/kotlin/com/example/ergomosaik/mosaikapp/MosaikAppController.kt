package com.example.ergomosaik.mosaikapp

import com.example.ergomosaik.mosaikapp.api.SkyHarborService
import org.ergoplatform.mosaik.*
import org.ergoplatform.mosaik.jackson.MosaikSerializer
import org.ergoplatform.mosaik.model.FetchActionResponse
import org.ergoplatform.mosaik.model.MosaikApp
import org.ergoplatform.mosaik.model.MosaikContext
import org.ergoplatform.mosaik.model.ui.ForegroundColor
import org.ergoplatform.mosaik.model.ui.IconType
import org.ergoplatform.mosaik.model.ui.Image
import org.ergoplatform.mosaik.model.ui.input.TextField
import org.ergoplatform.mosaik.model.ui.layout.Grid
import org.ergoplatform.mosaik.model.ui.layout.HAlignment
import org.ergoplatform.mosaik.model.ui.layout.Padding
import org.ergoplatform.mosaik.model.ui.layout.VAlignment
import org.ergoplatform.mosaik.model.ui.text.LabelStyle
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.servlet.http.HttpServletRequest

@RestController
@CrossOrigin
class MosaikAppController(
    private val skyHarborService: SkyHarborService,
) {

    @GetMapping(marketPlaceUrl)
    fun getMainApp(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "") search: String,
        @RequestHeader headers: Map<String, String>,
        request: HttpServletRequest,
    ): MosaikApp {
        return mosaikApp(
            "NFT Marketplace",
            mosaikAppVersion,
            "Buy NFTs and NFT collections",
        ) {
            val context = MosaikSerializer.fromContextHeadersMap(headers)
            val salesPerPage =
                if (context.walletAppPlatform == MosaikContext.Platform.PHONE) 12 else 24
            val nftSales = skyHarborService.getSales(salesPerPage, page, search)

            column(Padding.NONE, childAlignment = HAlignment.JUSTIFY) {

                val searchForRequest =
                    backendRequest(getHostUrl(request) + searchMarketPlacePostUrl)
                column(Padding.DEFAULT, childAlignment = HAlignment.JUSTIFY) {
                    textInputField(searchFieldId, "Search NFTs for sale", search) {
                        endIcon = IconType.SEARCH
                        onEndIconClicked = searchForRequest.id
                        imeActionType = TextField.ImeActionType.SEARCH
                        onImeAction = searchForRequest.id
                    }

                    // TODO
//                    label(
//                        "View and search collections",
//                        style = LabelStyle.BODY1BOLD,
//                        HAlignment.END,
//                        ForegroundColor.PRIMARY
//                    )
                }

                column(childAlignment = HAlignment.JUSTIFY) {
                    id = mainGridContainerId

                    val salesToShow =
                        nftSales.filter { it.currency == "erg" && it.nft_type == "image" }

                    grid(elementSize = Grid.ElementSize.MEDIUM) {

                        salesToShow
                            .forEach { nftSale ->

                                card(Padding.HALF_DEFAULT) {
                                    column(
                                        Padding.HALF_DEFAULT, spacing = Padding.HALF_DEFAULT
                                    ) {
                                        nftSale.ipfs_art_hash?.let {
                                            image(
                                                "https://cloudflare-ipfs.com/ipfs/$it",
                                                Image.Size.LARGE
                                            )
                                        }
                                        label(
                                            nftSale.collection_name,
                                            LabelStyle.BODY1BOLD,
                                            textColor = ForegroundColor.PRIMARY
                                        ) {
                                            maxLines = 1
                                        }
                                        label(nftSale.nft_name, LabelStyle.BODY1BOLD) {
                                            maxLines = 1
                                        }
                                        ergAmount(
                                            nftSale.nerg_sale_value,
                                            LabelStyle.HEADLINE2,
                                            trimTrailingZero = true
                                        )
                                        button("Details")
                                    }
                                }

                            }
                    }

                    if (salesToShow.isEmpty())
                        box(Padding.DEFAULT) {
                            label(
                                "No NFTs sales found matching your criteria",
                                LabelStyle.HEADLINE2,
                                HAlignment.CENTER
                            )
                        }

                    box(Padding.HALF_DEFAULT) {
                        layout(HAlignment.START, VAlignment.CENTER) {
                            if (page > 0)
                                button("Previous") {
                                    onClickAction(
                                        navigateToApp(newAppUrl(request, page - 1, search))
                                    )
                                }
                        }

                        layout(HAlignment.END, VAlignment.CENTER) {
                            if (nftSales.size >= salesPerPage)
                                button("Next") {
                                    onClickAction(
                                        navigateToApp(newAppUrl(request, page + 1, search))
                                    )
                                }
                        }
                    }
                }
            }

        }
    }

    @PostMapping(searchMarketPlacePostUrl)
    fun searchFor(
        @RequestHeader headers: Map<String, String>,
        @RequestBody values: Map<String, *>,
        request: HttpServletRequest
    ): FetchActionResponse {
        return backendResponse(
            mosaikAppVersion,
            navigateToApp(newAppUrl(request, 0, (values[searchFieldId] as? String) ?: ""))
        )
    }

    private fun newAppUrl(request: HttpServletRequest, page: Int, searchString: String): String {
        val hostUrl = getHostUrl(request)
        return "$hostUrl$marketPlaceUrl?page=$page" +
                (if (searchString.isNotBlank()) "&search=" + URLEncoder.encode(
                    searchString,
                    StandardCharsets.UTF_8.toString()
                ) else "")
    }

    private fun getHostUrl(request: HttpServletRequest): String {
        val requestUrl = request.requestURL.toString()
        val scheme = requestUrl.substringBefore("://")
        val host = requestUrl.substringAfter("://").substringBefore("/")
        return "$scheme://$host"
    }

}

const val marketPlaceUrl = "/"
const val searchMarketPlacePostUrl = "/search"
const val searchFieldId = "search"
const val mainGridContainerId = "main"