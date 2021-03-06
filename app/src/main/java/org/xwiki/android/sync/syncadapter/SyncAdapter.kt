package org.xwiki.android.sync.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import org.xwiki.android.sync.*
import org.xwiki.android.sync.bean.XWikiUserFull
import org.xwiki.android.sync.contactdb.ContactManager
import org.xwiki.android.sync.contactdb.setAccountContactsVisibility
import org.xwiki.android.sync.utils.ChannelJavaWaiter
import org.xwiki.android.sync.utils.StringUtils
import org.xwiki.android.sync.utils.awaitBlocking
import rx.Observer
import java.util.*

private const val TAG = "SyncAdapter"

class SyncAdapter(
    context: Context,
    autoInitialize: Boolean,
    allowParallelSyncs: Boolean
) : AbstractThreadedSyncAdapter(
    context,
    autoInitialize,
    allowParallelSyncs
) {
    /**
     * Account manager to manage synchronization.
     */
    private val mAccountManager: AccountManager = AccountManager.get(context)

    /**
     * @param context will be set to {@link #mContext}
     * @param autoInitialize auto initialization sync
     */
    constructor(
        context: Context,
        autoInitialize: Boolean
    ) : this(
        context,
        autoInitialize,
        false
    )

    private var updateThread: Thread? = null
        set(value) {
            synchronized(this) {
                field ?.also {
                    if (it.isAlive) {
                        it.interrupt()
                    }
                }
                field = value
            }
        }

    /**
     * Perform all sync process.
     *
     * @param account the account that should be synced
     * @param extras SyncAdapter-specific parameters
     * @param authority the authority of this sync request
     * @param provider a ContentProviderClient that points to the ContentProvider for this
     *   authority
     * @param syncResult SyncAdapter-specific parameters
     */
    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult
    ) {
        account ?: return
        setAccountContactsVisibility(
            context.contentResolver,
            account,
            false
        )

        val triggeringChannel = Channel<Boolean>(1)
        val channelJavaWaiter = ChannelJavaWaiter(appCoroutineScope, triggeringChannel)

        val updateJob = appCoroutineScope.async {
            Log.i(TAG, "onPerformSync start")
            val userAccount = userAccountsRepo.findByAccountName(account.name) ?: return@async null
            val syncType = userAccount.syncType

            Log.i(TAG, "syncType=$syncType")
            if (syncType == SYNC_TYPE_NO_NEED_SYNC) return@async null
            // get last sync date. return new Date(0) if first onPerformSync
            val lastSyncMarker = getServerSyncMarker(account)
            Log.d(TAG, lastSyncMarker)

            // Get XWiki SyncData from XWiki server , which should be added, updated or deleted after lastSyncMarker.
            val (observable, thread) = resolveApiManager(userAccount).xWikiHttp.getSyncData(
                syncType,
                userAccount.selectedGroupsList
            )

            thread.setUncaughtExceptionHandler { _, e ->
                observable.onError(e)
            }

            updateThread = thread

            // Update the local contacts database with the last modified changes. updateContact()
            ContactManager.updateContacts(context, userAccount, observable)

            observable.subscribe(
                object : Observer<XWikiUserFull> {
                    override fun onCompleted() {
                        updateServerSyncMarker(account)

                        triggeringChannel.offer(true)

                        setAccountContactsVisibility(
                            context.contentResolver,
                            account,
                            true
                        )
                    }

                    override fun onError(e: Throwable) {
                        syncResult.stats.numIoExceptions++

                        triggeringChannel.offer(true)

                        setAccountContactsVisibility(
                            context.contentResolver,
                            account,
                            true
                        )
                    }

                    override fun onNext(xWikiUserFull: XWikiUserFull) {
                        syncResult.stats.numEntries++
                    }
                }
            ).also {
                synchronized(observable) {
                    (observable as Object).notifyAll()
                }
            }
        }

        updateJob.invokeOnCompletion { error ->
            if (error != null) {
                triggeringChannel.offer(true)
            }
        }

        try {
            channelJavaWaiter.lock()
        } catch (e: InterruptedException) {
            updateJob.awaitBlocking(appCoroutineScope) ?.unsubscribe()
        } finally {
            updateThread = null
        }
    }

    override fun onSyncCanceled() {
        super.onSyncCanceled()
        updateThread = null
    }

    /**
     * This helper function fetches the last known high-water-mark
     * we received from the server - or 0 if we've never synced.
     *
     * @param account the account we're syncing
     * @return the change high-water-mark  Iso8601
     */
    private fun getServerSyncMarker(account: Account): String {
        val lastSyncIso = mAccountManager.getUserData(account, SYNC_MARKER_KEY)
        //if empty, just return new Date(0) so that we can get all users from server.
        return if (TextUtils.isEmpty(lastSyncIso)) {
            StringUtils.dateToIso8601String(Date(0))
        } else lastSyncIso
    }

    /**
     * Save off the high-water-mark we receive back from the server.
     *
     * @param account     The account we're syncing
     * @param lastSyncIso The high-water-mark we want to save.
     */
    private fun setServerSyncMarker(account: Account, lastSyncIso: String) {
        mAccountManager.setUserData(account, SYNC_MARKER_KEY, lastSyncIso)
    }

    private fun updateServerSyncMarker(account: Account) {
        // Save off the new sync date. On our next sync, we only want to receive
        // contacts that have changed since this sync...
        setServerSyncMarker(account, StringUtils.dateToIso8601String(Date()))
    }
}
