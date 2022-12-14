package com.example.ergomosaik.mosaikapp.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class NftSale(
    var id: Int,
    var ipfs_art_hash: String?,
    var nft_name: String,
    var nft_desc: String?,
    var collection_name: String,
    var collection_sys_name: String,
    var nft_type: String, // "image"
    var currency: String, // "erg"
    var nerg_sale_value: Long,
    var token_id: String,
    var token_amount: Long,
    var sales_address: String,
    var box_id: String,
)