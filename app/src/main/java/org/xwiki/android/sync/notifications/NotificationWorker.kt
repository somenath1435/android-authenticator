package org.xwiki.android.sync.notifications

import android.accounts.AccountManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.launch
import org.xwiki.android.sync.R
import org.xwiki.android.sync.activities.Notifications.NotificationsActivity
import org.xwiki.android.sync.appCoroutineScope
import org.xwiki.android.sync.bean.XWikiUserFull
import org.xwiki.android.sync.bean.notification.Notification
import org.xwiki.android.sync.resolveApiManager
import org.xwiki.android.sync.userAccountsRepo
import java.util.Random

class NotificationWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.e("Notification Worker", "Started")
        return try {
            val username = inputData.getString("username")
            if (!username.isNullOrEmpty())
                getNotifications(username)
            Result.success()
        } catch (t: Throwable) {
            Log.e("NotificationWorker", t.message)
            Result.failure()
        }
    }

    private fun createNotification(title: String, description: String, link: String) {

        var notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel("101", "xwiki_channel", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(applicationContext, "101")
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(browserIntent(applicationContext, link))
            .addAction(R.mipmap.ic_launcher, "View", browserIntent(applicationContext, link))
            .setAutoCancel(true)

        val random = Random()
        
        notificationManager.notify(random.nextInt(100000), notificationBuilder.build())

    }

    private fun browserIntent(context: Context, link: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(link))
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // this intent opens notification activity
    private fun pendingIntent(context: Context, username: String?): PendingIntent {
        val intent = Intent(context, NotificationsActivity::class.java)
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, username)
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getNotifications(username: String) {
        appCoroutineScope.launch {
            val userAccount =
                userAccountsRepo.findByAccountName(username) ?: return@launch
            val apiManager = resolveApiManager(userAccount)
            val userId = "xwiki" + ":" + "XWiki" + "." + username
            apiManager.xwikiServicesApi.getNotify(userId, true, true)
                .subscribe(
                    {

                        var notificationList: List<Notification>? = null
                        if (!it.notifications.isNullOrEmpty()) notificationList = it.notifications
                        else if (it.asyncId != null) {
                            do {
                                var id = it.asyncId
                                if (id != null) {
                                    apiManager.xwikiServicesApi.getNotifyAsync(
                                        userId,
                                        true,
                                        true,
                                        id
                                    )
                                        .subscribe(
                                            {
                                                Log.e("AsyncId", it.asyncId.toString())
                                                Log.e("AsyncId", it.notifications.toString())
                                                id = it.asyncId
                                                notificationList = it.notifications
                                            },
                                            {
                                                it.printStackTrace()
                                            }
                                        )
                                }
                            } while (id != null)
                        }

                        Log.e("FinalList", notificationList.toString())


                        notificationList?.forEach {
                            Log.e(
                                "NotificationWorker",
                                it.document.toString() + it.type.toString()
                            )

                            val splittedDocument = XWikiUserFull.splitDocument(it.document)

                            Log.e("NotificationWorker", splittedDocument)

                            var url: String? = null
                            var title: String? = null

                            apiManager.xwikiServicesApi.getPageDetails(splittedDocument)
                                .subscribe(
                                    {
                                        Log.e("XwikiAbsoluteUrl", it.xwikiAbsoluteUrl + "")
                                        if (!it.xwikiAbsoluteUrl.isNullOrEmpty()) {
                                            url = it.xwikiAbsoluteUrl.toString()
                                            title = it.title.toString()
                                        }
                                    },
                                    {
                                        it.printStackTrace()
                                    }
                                )

                            if (!url.isNullOrEmpty())
                                createNotification(it.type.toString(), title.toString(), url.toString()
                                )

                        }
                    },
                    {
                        Log.e("Error", it.message)
                    }
                )
        }
    }
}