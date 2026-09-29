package fr.croustille.data

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path

interface CroustillantApi {
    @GET("v1/regions")
    suspend fun regions(): ApiWrapper<List<Region>>

    @GET("v1/regions/{code}/restaurants")
    suspend fun restaurantsDeRegion(@Path("code") code: Int): ApiWrapper<List<Restaurant>>

    @GET("v1/regions/25/restaurants")
    suspend fun restaurantsStrasbourg(): ApiWrapper<List<Restaurant>>

    @GET("v1/restaurants/{code}")
    suspend fun restaurant(@Path("code") code: Int): ApiWrapper<Restaurant>

    @GET("v1/restaurants/{code}/menu")
    suspend fun menusAVenir(@Path("code") code: Int): ApiWrapper<List<MenuJour>>

    @GET("v1/restaurants/{code}/menu/{date}")
    suspend fun menuDuJour(
        @Path("code") code: Int,
        @Path("date") date: String, // DD-MM-YYYY
    ): ApiWrapper<MenuJour>

    companion object {
        fun create(): CroustillantApi {
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
            val client = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.croustillant.menu/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(CroustillantApi::class.java)
        }
    }
}
