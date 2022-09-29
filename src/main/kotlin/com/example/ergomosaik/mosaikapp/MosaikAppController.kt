package com.example.ergomosaik.mosaikapp

import com.example.ergomosaik.mosaikapp.api.NftSale
import com.example.ergomosaik.mosaikapp.api.SkyHarborService
import org.ergoplatform.mosaik.*
import org.ergoplatform.mosaik.jackson.MosaikSerializer
import org.ergoplatform.mosaik.model.*
import org.ergoplatform.mosaik.model.ui.ForegroundColor
import org.ergoplatform.mosaik.model.ui.IconType
import org.ergoplatform.mosaik.model.ui.Image
import org.ergoplatform.mosaik.model.ui.input.TextField
import org.ergoplatform.mosaik.model.ui.layout.*
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
        @RequestParam(defaultValue = "") collection: String,
        @RequestHeader headers: Map<String, String>,
        request: HttpServletRequest,
    ): MosaikApp {
        return mosaikApp(
            "NFT Marketplace" +
                    (if (search.isNotEmpty()) " Search" else "") +
                    (if (collection.isNotEmpty()) " Collection ${
                        skyHarborService.getCollection(collection)?.name ?: "-"
                    }" else "") +
                    (if (page > 0) " Page ${page + 1}" else ""),
            mosaikAppVersion,
            (if (collection.isNotEmpty()) skyHarborService.getCollection(collection)?.description
            else null) ?: "Buy NFTs and NFT collections",
        ) {
            val context = MosaikSerializer.fromContextHeadersMap(headers)
            val salesPerPage =
                if (context.walletAppPlatform == MosaikContext.Platform.PHONE) 6 else 12
            val nftSales = skyHarborService.getSales(salesPerPage, page, search, collection)

            column(Padding.NONE, childAlignment = HAlignment.JUSTIFY) {

                if (collection.isNotEmpty()) layout(HAlignment.CENTER) {
                    val nftCollection = skyHarborService.getCollection(collection)
                    label(
                        "Showing collection ${nftCollection?.name ?: collection}",
                        LabelStyle.HEADLINE2,
                        HAlignment.CENTER
                    )
                    label(
                        "Show all collections",
                        LabelStyle.BODY1BOLD,
                        HAlignment.CENTER,
                        textColor = ForegroundColor.PRIMARY
                    ) {
                        onClickAction = navigateToApp(newAppUrl(request, 0, "", "")).id
                    }
                }

                val searchForRequest =
                    backendRequest(
                        getHostUrl(request) + searchMarketPlacePostUrl + "?collection=" +
                                URLEncoder.encode(collection, Charsets.UTF_8)
                    )
                column(Padding.DEFAULT, childAlignment = HAlignment.JUSTIFY) {
                    textInputField(searchFieldId, "Search NFTs for sale", search) {
                        endIcon = IconType.SEARCH
                        onEndIconClicked = searchForRequest.id
                        imeActionType = TextField.ImeActionType.SEARCH
                        onImeAction = searchForRequest.id
                        minValue = 1
                    }

                    layout(HAlignment.END) {
                        label(
                            "View available collections",
                            style = LabelStyle.BODY1BOLD,
                            HAlignment.END,
                            ForegroundColor.PRIMARY
                        ) {
                            onClickAction = changeView(showCollectionView(request)).id
                        }
                    }
                }

                column(childAlignment = HAlignment.JUSTIFY) {
                    id = mainGridContainerId

                    val salesToShow =
                        nftSales.filter { it.currency == "erg" && it.nft_type == "image" }

                    grid(elementSize = Grid.ElementSize.MEDIUM) {

                        salesToShow.forEach { nftSale ->
                            card(Padding.HALF_DEFAULT) {
                                saleCardContent(this@mosaikApp, request, nftSale, false)
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
                                        navigateToApp(
                                            newAppUrl(
                                                request,
                                                page - 1,
                                                search,
                                                collection
                                            )
                                        )
                                    )
                                }
                        }

                        layout(HAlignment.END, VAlignment.CENTER) {
                            if (nftSales.size >= salesPerPage)
                                button("Next") {
                                    onClickAction(
                                        navigateToApp(
                                            newAppUrl(
                                                request,
                                                page + 1,
                                                search,
                                                collection
                                            )
                                        )
                                    )
                                }
                        }
                    }
                }
            }

        }
    }

    private fun Card.saleCardContent(
        mosaikApp: MosaikApp,
        request: HttpServletRequest,
        nftSale: NftSale,
        isDetailPage: Boolean,
    ) {
        val toSalesDetailsId =
            mosaikApp.navigateToSaleAction(request, nftSale.id).id
        if (!isDetailPage)
            onClickAction = toSalesDetailsId
        column(
            Padding.HALF_DEFAULT, spacing = Padding.HALF_DEFAULT
        ) {
            nftSale.ipfs_art_hash?.let {
                image(
                    "https://cloudflare-ipfs.com/ipfs/$it",
                    if (isDetailPage) Image.Size.XXL else Image.Size.LARGE
                )
            }
            label(
                nftSale.collection_name,
                LabelStyle.BODY1BOLD,
                textColor = ForegroundColor.PRIMARY
            ) {
                maxLines = 1
                onClickAction = mosaikApp.navigateToCollectionAction(
                    request,
                    nftSale.collection_sys_name
                ).id

            }
            if (isDetailPage) {
                tokenLabel(
                    nftSale.token_id,
                    nftSale.nft_name,
                    nftSale.token_amount,
                    style = LabelStyle.BODY1BOLD
                )

                nftSale.nft_desc?.let {
                    label(it, LabelStyle.BODY2, HAlignment.CENTER)
                }
            } else
                label(nftSale.nft_name, LabelStyle.BODY1BOLD) {
                    maxLines = 1
                }

            ergAmount(
                nftSale.nerg_sale_value,
                LabelStyle.HEADLINE2,
                trimTrailingZero = true
            )
            button(if (isDetailPage) "Purchase" else "Details") {
                onClickAction(toSalesDetailsId)
            }
        }
    }

    private fun showCollectionView(request: HttpServletRequest) = mosaikView {
        column(Padding.HALF_DEFAULT, spacing = Padding.DEFAULT) {
            label("Top collections", LabelStyle.HEADLINE2)
            grid(elementSize = Grid.ElementSize.LARGE) {
                skyHarborService.getTopCollections().forEach { collection ->
                    collection?.let {
                        card(Padding.HALF_DEFAULT) {
                            onClickAction =
                                navigateToCollectionAction(request, collection.sys_name).id

                            row(Padding.HALF_DEFAULT, spacing = Padding.HALF_DEFAULT) {
                                image(collection.card_image, Image.Size.MEDIUM)

                                layout(weight = 1) {
                                    label(collection.name, LabelStyle.HEADLINE2)
                                }
                            }
                        }
                    }
                }
            }
            label("All collections", LabelStyle.HEADLINE2)
            grid(elementSize = Grid.ElementSize.MEDIUM) {
                skyHarborService.getCollections().forEach { collection ->
                    box(Padding.DEFAULT) {
                        onClickAction =
                            navigateToApp(newAppUrl(request, 0, "", collection.sys_name)).id

                        label(collection.name, LabelStyle.HEADLINE2, HAlignment.CENTER)
                    }
                }
            }
        }
    }

    private fun ViewContent.navigateToCollectionAction(
        request: HttpServletRequest,
        collectionSysName: String
    ) = navigateToApp(
        newAppUrl(request, 0, "", collectionSysName),
        id = "openCollection${collectionSysName}"
    )

    private fun ViewContent.navigateToSaleAction(
        request: HttpServletRequest,
        sale: Int
    ) = navigateToApp(
        getHostUrl(request) + saleUrl + "/$sale",
        id = "openSale${sale}"
    )

    @GetMapping(saleUrl + "/{sale}")
    fun saleDetails(
        @RequestHeader headers: Map<String, String>,
        @PathVariable(name = "sale") saleId: Int,
        request: HttpServletRequest
    ): MosaikApp {
        val sale = skyHarborService.getSale(saleId)
        return mosaikApp(
            "NFT Sale " + (sale?.nft_name ?: ""),
            mosaikAppVersion,
            null,
            targetCanvasDimension = MosaikManifest.CanvasDimension.MEDIUM_WIDTH,
        ) {
            box(Padding.DEFAULT) {
                column(spacing = Padding.DEFAULT) {
                    sale?.let {
                        card(Padding.NONE) {
                            saleCardContent(this@mosaikApp, request, sale, true)
                        }
                    }

                    if (sale == null) {
                        label("Sale was not found", LabelStyle.HEADLINE2, HAlignment.CENTER)
                    }

                    label(
                        "Back to marketplace",
                        LabelStyle.BODY1LINK,
                        HAlignment.CENTER,
                        ForegroundColor.PRIMARY
                    ) {
                        onClickAction = navigateToApp(newAppUrl(request, 0, "", "")).id
                    }
                }
            }
        }
    }

    @PostMapping(searchMarketPlacePostUrl)
    fun searchFor(
        @RequestHeader headers: Map<String, String>,
        @RequestParam collection: String,
        @RequestBody values: Map<String, *>,
        request: HttpServletRequest
    ): FetchActionResponse {
        return backendResponse(
            mosaikAppVersion,
            navigateToApp(
                newAppUrl(
                    request,
                    0,
                    (values[searchFieldId] as? String) ?: "",
                    collection
                )
            )
        )
    }

    private fun newAppUrl(
        request: HttpServletRequest,
        page: Int,
        searchString: String,
        collection: String
    ): String {
        val hostUrl = getHostUrl(request)
        return "$hostUrl$marketPlaceUrl?page=$page" +
                (if (searchString.isNotBlank()) "&search=" + URLEncoder.encode(
                    searchString, StandardCharsets.UTF_8
                ) else "") +
                (if (collection.isNotBlank()) "&collection=" + URLEncoder.encode(
                    collection, StandardCharsets.UTF_8
                )
                else "")
    }

    private fun getHostUrl(request: HttpServletRequest): String {
        val requestUrl = request.requestURL.toString()
        val scheme = requestUrl.substringBefore("://")
        val host = requestUrl.substringAfter("://").substringBefore("/")
        return "$scheme://$host"
    }

}

const val marketPlaceUrl = "/"
const val saleUrl = "/sale"
const val searchMarketPlacePostUrl = "/search"
const val searchFieldId = "search"
const val mainGridContainerId = "main"