package com.liuguang.media.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface VodApiService {
    @GET
    suspend fun getVodList(
        @Url url: String,
        @Query("ac") ac: String = "videolist",
        @Query("pg") page: Int? = null,
        @Query("t") typeId: Int? = null,
        @Query("wd") keyword: String? = null
    ): VodApiResponse

    @GET
    suspend fun getVodDetail(
        @Url url: String,
        @Query("ac") ac: String = "detail",
        @Query("ids") ids: String
    ): VodApiResponse
}
