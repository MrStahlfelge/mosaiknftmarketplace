package com.example.ergomosaik.mosaikapp.api

import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder

@Service
class SkyHarborService {
    private var mainPageNftList: List<NftSale> = emptyList()
    private var minPageSalesLastUpdatedMs: Long = 0

    fun getSales(
        salesPerPage: Int,
        page: Int = 0,
        search: String = "",
        collection: String = "",
    ): List<NftSale> {
        val isMainPage = page == 0 && search.isEmpty() && collection.isEmpty()

        return if (isMainPage && System.currentTimeMillis() - minPageSalesLastUpdatedMs < 5 * 60 * 1000L)
            mainPageNftList.take(salesPerPage)
        else {
            val offset = salesPerPage * page
            val retList = try {
                RestTemplate().getForEntity(
                    "https://skyharbor-server.net/api/sales?status=active&orderCol=list_time&order=desc&limit=${if (isMainPage) 25 else salesPerPage}&offset=$offset" +
                            (if (search.isNotBlank())
                                "&searchFor=" + URLEncoder.encode(search, Charsets.UTF_8)
                            else "") +
                            (if (collection.isNotEmpty())
                                "&collection=" + URLEncoder.encode(collection, Charsets.UTF_8)
                            else ""),
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

    private val collectionList = HashMap<String, NftCollection>()
    private var topCollections: List<String> = emptyList()
    private var collectionUpdatedMs: Long = 0

    fun getCollections(): List<NftCollection> {
        return updateCollections().values.sortedBy { it.name.lowercase() }
    }

    private fun updateCollections(): Map<String, NftCollection> {
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
                collectionList.clear()
                newCollectionList.forEach { collectionList[it.sys_name] = it }
                collectionUpdatedMs = System.currentTimeMillis()

                // top collections
                val topCollections = try {
                    RestTemplate().getForEntity(
                        "https://skyharbor-server.net/api/metrics/topVolumes?limit=12",
                        Array<NftCollectionVolume>::class.java
                    ).body?.toList()
                } catch (t: Throwable) {
                    null
                } ?: emptyList()
                this.topCollections = topCollections.map { it.collection }
            }
        }

        return collectionList
    }

    fun getTopCollections(): List<NftCollection?>  {
        val map = updateCollections()
        return topCollections.map { map[it] }
    }

    fun getCollection(sysname: String): NftCollection? =
        updateCollections()[sysname]
}