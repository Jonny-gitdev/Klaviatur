package de.klaviatur.data.db

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

// ── Response models ───────────────────────────────────────────────────────────

data class OOStatus(val success: String?, val error: String?)

data class OOComposer(
    val id: String,
    val name: String?,
    @SerializedName("complete_name") val completeName: String?,
    val epoch: String?
)

data class OOWork(
    val id: String,
    val title: String,
    val subtitle: String?,
    val composer: OOComposer?
)

data class OOOmniResult(
    val composer: OOComposer?,
    val work: OOWork?
)

data class OOComposerResponse(val status: OOStatus?, val composers: List<OOComposer>?)
data class OOWorkResponse(val status: OOStatus?, val works: List<OOWork>?, val composer: OOComposer?)
data class OOOmniResponse(val status: OOStatus?, val results: List<OOOmniResult>?)

// ── Retrofit interface ────────────────────────────────────────────────────────

interface OpenOpusApi {
    @GET("composer/list/search/{query}.json")
    suspend fun searchComposers(@Path("query") query: String): OOComposerResponse

    @GET("work/list/search/{query}.json")
    suspend fun searchWorks(@Path("query") query: String): OOWorkResponse

    @GET("work/list/composer/{composerId}/genre/all.json")
    suspend fun getWorksForComposer(@Path("composerId") composerId: String): OOWorkResponse

    @GET("work/list/composer/{composerId}/genre/all/search/{query}.json")
    suspend fun searchWorksForComposer(
        @Path("composerId") composerId: String,
        @Path("query") query: String
    ): OOWorkResponse

    @GET("omnisearch/{query}/{offset}.json")
    suspend fun omnisearch(
        @Path("query") query: String,
        @Path("offset") offset: Int = 0
    ): OOOmniResponse
}

data class OOSearchResult(
    val title: String,
    val subtitle: String?,
    val composer: String
)
