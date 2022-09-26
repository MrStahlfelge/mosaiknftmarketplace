package com.example.ergomosaik.mosaikapp.api

import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder

@Service
class SkyHarborService {
    fun getSales(salesPerPage: Int, page: Int = 0, search: String = ""): List<NftSale> {
        val offset = salesPerPage * page

        return RestTemplate().getForEntity(
            "https://skyharbor-server.net/api/sales?status=active&orderCol=list_time&order=desc&limit=$salesPerPage&offset=$offset" +
                    if (search.isNotBlank())
                        "&searchFor=" + URLEncoder.encode(search, Charsets.UTF_8)
                    else "",
            Array<NftSale>::class.java
        ).body?.toList() ?: emptyList()
    }
}