package com.example.ergomosaik.mosaikapp.api

import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder

@Service
class SkyHarborService {
    private var mainPageNftList: List<NftSale> = emptyList()
    private var minPageSalesLastUpdatedMs: Long = 0

    fun getSales(salesPerPage: Int, page: Int = 0, search: String = ""): List<NftSale> {
        val isMainPage = page == 0 && search.isEmpty()

        return if (isMainPage && System.currentTimeMillis() - minPageSalesLastUpdatedMs < 5 * 60 * 1000L)
            mainPageNftList.take(salesPerPage)
        else {
            val offset = salesPerPage * page
            val retList = try {
                RestTemplate().getForEntity(
                    "https://skyharbor-server.net/api/sales?status=active&orderCol=list_time&order=desc&limit=${if (isMainPage) 25 else salesPerPage}&offset=$offset" +
                            if (search.isNotBlank())
                                "&searchFor=" + URLEncoder.encode(search, Charsets.UTF_8)
                            else "",
                    Array<NftSale>::class.java
                ).body?.toList()
            } catch (t: Throwable) {
                null
            } ?: emptyList()

            // we cache the main page for five minutes. Not perfect, parallel call could happen - but there's no harm.
            if (isMainPage && retList.isNotEmpty()) {
                mainPageNftList = retList
                minPageSalesLastUpdatedMs = System.currentTimeMillis()
            }

            retList.take(salesPerPage)
        }
    }

    private var collectionList: List<NftCollection> = emptyList()
    private var collectionUpdatedMs: Long = 0

    fun getCollections(): List<NftCollection> {
        if (System.currentTimeMillis() - collectionUpdatedMs > 6 * 60 * 60L * 1000) {
            val newCollectionList = try {
                RestTemplate().getForEntity(
                    "https://skyharbor-server.net/api/collections",
                    Array<NftCollection>::class.java
                ).body?.toList()
            } catch (t: Throwable) {
                null
            } ?: emptyList()

            if (newCollectionList.isNotEmpty()) {
                collectionList = newCollectionList
                collectionUpdatedMs = System.currentTimeMillis()
            }
        }

        return collectionList
    }
}