package com.example.ergomosaik.mosaikapp.api

import com.example.ergomosaik.mosaikapp.ergo.PeerService
import com.example.ergomosaik.mosaikapp.formatErgAmount
import org.ergoplatform.appkit.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import special.collection.Coll
import special.sigma.Box
import java.net.URLEncoder

@Service
class SkyHarborService(
    private val peerService: PeerService,
) {
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

    fun getSale(salesId: Int): NftSale? {
        val retList = try {
            RestTemplate().getForEntity(
                "https://skyharbor-server.net/api/sales?saleId=$salesId",
                Array<NftSale>::class.java
            ).body?.toList()
        } catch (t: Throwable) {
            null
        } ?: emptyList()
        return retList.firstOrNull()
    }

    private val collectionList = HashMap<String, NftCollection>()
    private var topCollections: List<String> = emptyList()
    private var collectionUpdatedMs: Long = 0

    fun getCollections(): List<NftCollection> {
        return updateCollections().values.sortedBy { it.name.lowercase() }
    }

    private fun updateCollections(): Map<String, NftCollection> {
        if (System.currentTimeMillis() - collectionUpdatedMs > 6 * 60 * 60L * 1000 || collectionList.isEmpty()) {
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

    fun getTopCollections(): List<NftCollection?> {
        val map = updateCollections()
        return topCollections.map { map[it] }
    }

    fun getCollection(sysname: String): NftCollection? =
        updateCollections()[sysname]

    fun buildPurchaseTransaction(salesId: Int, buyerAddress: String): Pair<String, ByteArray> {
        val sale = getSale(salesId)

        if (sale == null || sale.sales_address != ergSalesAddress) {
            throw IllegalArgumentException("Sale $salesId not found.")
        }

        val ergoClient = peerService.getErgoClient()

        val salesBox = ergoClient.dataSource.getBoxById(sale.box_id)
        val price = (salesBox.registers[0].value as Number).toLong()
        val seller = Address.fromPropositionBytes(
            NetworkType.MAINNET,
            ScalaHelpers.collByteToByteArray(salesBox.registers[1].value as Coll<Byte>)
        )
        val royaltyBox = salesBox.registers[2].value as? Box
        val royalty =
            (royaltyBox?.registers()?.getOrElse(4, null)?.value() as? Number ?: 0).toLong()
        val nftCreator = if (royalty > 0) royaltyBox?.let {
            Address.fromPropositionBytes(
                NetworkType.MAINNET,
                ScalaHelpers.collByteToByteArray(royaltyBox.propositionBytes() as Coll<Byte>)
            )
        } else null
        val skyHarborFee = price / 50
        val royaltyFee = price * royalty / 1000

        return ergoClient.execute { ctx ->

            val boxOperations = BoxOperations.createForSender(Address.create(buyerAddress), ctx)
                .withAmountToSpend(price)
                .withInputBoxesLoader(ExplorerAndPoolUnspentBoxesLoader())

            val inputs = boxOperations
                .loadTop().toMutableList()
            inputs.add(salesBox)

            val txb = ctx.newTxBuilder()
            val payToSeller = txb.outBoxBuilder()
                .value(price - royaltyFee - skyHarborFee)
                .contract(seller.toErgoContract())
                .registers(ErgoValue.of(salesBox.id.bytes))
                .build()
            val payToService = txb.outBoxBuilder()
                .value(skyHarborFee)
                .contract(Address.create(serviceFeeAddress).toErgoContract())
                .build()

            val outputs = mutableListOf(payToSeller, payToService)

            if (royaltyFee > 0) {
                outputs.add(
                    txb.outBoxBuilder()
                        .value(royaltyFee)
                        .contract(nftCreator!!.toErgoContract())
                        .build()
                )
            }

            outputs.add(
                txb.outBoxBuilder()
                    .value(salesBox.value)
                    .tokens(*salesBox.tokens.toTypedArray())
                    .contract(boxOperations.senders.first().toErgoContract())
                    .build()
            )

            val selectedBoxes =
                BoxSelectorsJavaHelpers.selectBoxes(
                    inputs,
                    outputs.sumOf { it.value },
                    salesBox.tokens
                )

            val unsigned = txb.boxesToSpend(selectedBoxes)
                .fee(boxOperations.feeAmount)
                .outputs(*outputs.toTypedArray())
                .sendChangeTo(boxOperations.senders.first().ergoAddress)
                .build()

            Pair(
                "Purchase price: ${formatErgAmount(price)} ERG",
                ctx.newProverBuilder().build().reduce(unsigned, 0).toBytes()
            )
        }
    }

    companion object {
        const val ergSalesAddress =
            "26tpZU6i6zeBRuzZVo7tr47zjArAxkHP8x7ijiRbbrvLhySvSU84bH8wNPGGG27EwhJfJLe7bvRPv6B1jQFQqrWUQqBX1XJpVGoiNgveance6JFZ4mKv1KkRE8nBSB3jKBGnVJjJF6wR1Z8YXRsUqrTff4bfmtbhaRRjibnDDtKhS71spfjjTBeU1AhhQpitCDg4NFxmTLyV1arE7G2riZKzDryjWnCiEJGzWNxYtVt8uDxd3qNSRE5sHECwcsb98x7rn4q4FyHMvvWrRMPFfVgAQd5wHCAHwhMEdqUrSFQVkmUMavju8CLAgCNcVFjUBKPX4ooEHLUw3QkxS9Jp6fAFAGmzJ6QVD71mAZYMYhoEQnFyUBx1txJjVJjCrcZsW43dimbt5su4ahATJ8qRtWgwat8vTViTVXAcBmUSnqbqhAqTCxcsS5EFS6ApJSfthPHYUyXwtcbTptfdnUx1e5hEiGcwxoQ8ivufNNiZE9xkxi4nsBBrBVBJ7pfSSoHvbodkzLrq91RHYrvuatyLuBSxgJxs198xUQhULqxmWwgthJLrG5VVfVYH"

        const val serviceFeeAddress = "9h9ssEYyHaosFg6BjZRRr2zxzdPPvdb7Gt7FA8x7N9492nUjpsd"
    }
}