/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.android.sync.rest

import android.accounts.Account
import android.accounts.AccountManager
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.xwiki.android.sync.ACCESS_TOKEN
import org.xwiki.android.sync.ACCOUNT_TYPE
import org.xwiki.android.sync.appContext
import org.xwiki.android.sync.contactdb.UserAccountId
import org.xwiki.android.sync.contactdb.abstracts.UserAccountsCookiesRepository
import org.xwiki.android.sync.userAccountsCookiesRepo
import org.xwiki.android.sync.utils.destructTempName
import retrofit2.Retrofit
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Will help to contain and separate functionality of working with API
 *
 * Base constructor which create [OkHttpClient] with [XWikiInterceptor] which will
 * be used in [Retrofit] for correct handling of requests.
 *
 * @param baseURL Url which will be used as base for all requests in this manager. Can be:
 *
 *  * http://www.xwiki.org/xwiki/
 *  * http://some.site.url/xwiki/
 *  * http://123.231.213.132/xwiki/
 *  * http://123.231.213.132:123456/xwiki/
 *
 * Strongly recommended to end url with
 *
 * @version $Id: 5f6167ab1821c765e6c4f42549b4447481e37cbc $
 */
class BaseApiManager(
    baseURL: String,
    userAccountId: UserAccountId,
    userAccountsCookiesRepository: UserAccountsCookiesRepository,
    accountName: String
) {

    /**
     * Main services which provide work with auth, getting groups/users and other
     */
    /**
     * @return [.xWikiServices]
     */
    val xwikiServicesApi: XWikiServices

    /**
     * Helper which work with downloading and managing of photos
     *
     * @return [.xWikiPhotosManager]
     *
     * @since 0.4
     */
    val xWikiPhotosManager: XWikiPhotosManager

    val xWikiHttp: XWikiHttp = XWikiHttp(this, userAccountId)

    init {
        var baseUrl = baseURL

        val accountName = accountName.destructTempName

        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        val account = Account(accountName, ACCOUNT_TYPE)
        val am = AccountManager.get(appContext)
        val accountPassword = am.getUserData(account,AccountManager.KEY_PASSWORD)
        val authToken = am.getUserData(account, ACCESS_TOKEN)
        val cookie = userAccountsCookiesRepo[userAccountId]

        val okHttpClient = if (cookie.isNullOrEmpty()) {
            OkHttpClient.Builder()
                .authenticator { route, response ->
                    val actualPassword = am.getUserData(account,AccountManager.KEY_PASSWORD) ?: return@authenticator null
                    val credential = Credentials.basic(accountName, actualPassword)
                    return@authenticator response.request().newBuilder().header("Authorization", credential).build()
                }
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(XWikiInterceptor(userAccountId, userAccountsCookiesRepository, authToken))
                .addInterceptor(loggingInterceptor)
                .build()
        } else {
            OkHttpClient.Builder()
                .authenticator { route, response ->
                    val credential = Credentials.basic(accountName, accountPassword)
                    return@authenticator response.request().newBuilder().header("Authorization", credential).build()
                }
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(XWikiInterceptor(userAccountId, userAccountsCookiesRepository, cookie))
                .addInterceptor(loggingInterceptor)
                .build()
        }

        // Check that url ends with `/` and put it if not
        if (!baseUrl.endsWith("/")) {
            baseUrl = "$baseUrl/"
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
            .client(okHttpClient)
            .build()

        xwikiServicesApi = initXWikiServices(retrofit)
        xWikiPhotosManager = initXWikiPhotosManager(okHttpClient, baseUrl)
    }

    /**
     * Create [XWikiServices] using given [Retrofit] instance
     *
     * @param retrofit Context for creation object
     * @return Created object
     * @see Retrofit.create
     * @since 0.4
     */
    private fun initXWikiServices(
        retrofit: Retrofit
    ): XWikiServices {
        return retrofit.create(XWikiServices::class.java)
    }

    /**
     * Create [XWikiPhotosManager] using given [OkHttpClient] client and base url
     *
     * @param client Will be used as one point of requests for result instance
     * @param baseUrl Will be used as root for all requests
     * @return Instance
     * @see XWikiPhotosManager.XWikiPhotosManager
     * @since 0.4
     */
    private fun initXWikiPhotosManager(
        client: OkHttpClient,
        baseUrl: String
    ): XWikiPhotosManager {
        return XWikiPhotosManager(client, baseUrl)
    }
}
