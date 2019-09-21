package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID


object SyncConst {

    const val INTENT_FILTER = "SettingsSyncFragment"
    const val ACTION_COMPLETED_DIALOG = "$ID.$INTENT_FILTER.ACTION_COMPLETED_DIALOG"
    const val ACTION_PROGRESS_DIALOG = "$ID.$INTENT_FILTER.ACTION_PROGRESS_DIALOG"
    const val ACTION = "$ID.$INTENT_FILTER.ACTION"
    const val EXTRA_PROGRESS = "$ID.$INTENT_FILTER.EXTRA_PROGRESS"
    const val EXTRA_AMOUNT = "$ID.$INTENT_FILTER.EXTRA_AMOUNT"
    const val EXTRA_ERRORS = "$ID.$INTENT_FILTER.EXTRA_ERRORS"
    const val EXTRA_CONTENT = "$ID.$INTENT_FILTER.EXTRA_CONTENT"

}