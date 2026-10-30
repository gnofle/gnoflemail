package com.fsck.k9.activity

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.k9mail.legacy.message.controller.MessageCountsProvider
import app.k9mail.legacy.message.controller.SimpleMessagingListener
import com.fsck.k9.controller.MessagingControllerWrapper
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.BaseActivity
import com.fsck.k9.ui.settings.SettingsActivity
import com.fsck.k9.ui.settings.resolveAppLogoResId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import kotlin.math.ceil
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import org.koin.android.ext.android.inject

private val HeaderBlue = Color(0xFF1976D2)
private val PageBackground = Color(0xFFF8F9FD)
private val TextDark = Color(0xFF14143C)
private val TextGrey = Color(0xFF6B6B7B)
private val IconGrey = Color(0xFF55556A)
private val AddButtonBackground = Color(0xFFDCE8F8)

private data class AccountListItem(
    val id: AccountId,
    val name: String,
    val lastSyncTime: Long,
    val color: Int,
    val unreadCount: Int,
)

/**
 * Start screen: the list of configured mailboxes with their unread counts.
 * Tapping a mailbox opens its inbox.
 */
class AccountListActivity : BaseActivity() {
    private val accountManager: LegacyAccountManager by inject()
    private val messageCountsProvider: MessageCountsProvider by inject()
    private val messagingController: MessagingControllerWrapper by inject()
    private val themeProvider: FeatureThemeProvider by inject()
    private val appNameProvider: AppNameProvider by inject()

    private var nameFontSize by mutableStateOf(DEFAULT_NAME_FONT_SIZE)
    private var fontSizeToast: Toast? = null

    // How many mail checks are running for each account (the refresh arrows turn while this is above 0)
    private var syncCounts by mutableStateOf<Map<AccountId, Int>>(emptyMap())

    // Accounts still being checked after a tap on the main refresh button (its arrow turns until this is empty)
    private var refreshAllPending by mutableStateOf<Set<AccountId>>(emptySet())

    private val checkMailListener = object : SimpleMessagingListener() {
        override fun checkMailFinished(context: Context?, accountId: AccountId?) {
            if (accountId != null) {
                runOnUiThread { onCheckMailFinished(accountId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        nameFontSize = getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE)
            .getInt(KEY_NAME_FONT_SIZE, DEFAULT_NAME_FONT_SIZE)
            .coerceIn(MIN_NAME_FONT_SIZE, MAX_NAME_FONT_SIZE)

        val logoResId = resolveAppLogoResId(this)
        val appName = appNameProvider.appName

        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    themeProvider.WithTheme {
                        val itemsFlow = remember { observeAccountItems() }
                        val items by itemsFlow.collectAsState(initial = emptyList())

                        AccountListScreen(
                            logoResId = logoResId,
                            appName = appName,
                            items = items,
                            nameFontSize = nameFontSize,
                            syncingAccountIds = syncCounts.keys,
                            refreshingAll = refreshAllPending.isNotEmpty(),
                            onAccountClick = { openInbox(it) },
                            onAccountRefresh = { refreshAccount(it) },
                            onRefreshAll = { refreshAll(items.map { item -> item.id }) },
                            onSettingsClick = { SettingsActivity.launch(this@AccountListActivity) },
                        )
                    }
                }
            },
        )
    }

    /** Volume up / down change the text size of the mailbox list by one point. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.repeatCount == 0) changeFontSize(+1)
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.repeatCount == 0) changeFontSize(-1)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun changeFontSize(delta: Int) {
        val newSize = (nameFontSize + delta).coerceIn(MIN_NAME_FONT_SIZE, MAX_NAME_FONT_SIZE)
        if (newSize != nameFontSize) {
            nameFontSize = newSize
            getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_NAME_FONT_SIZE, newSize)
                .apply()
        }

        fontSizeToast?.cancel()
        fontSizeToast = Toast.makeText(this, getString(R.string.gnofle_accounts_font_size, newSize), Toast.LENGTH_SHORT)
            .also { it.show() }
    }

    private fun openInbox(accountId: AccountId) {
        val intent = MessageHomeActivity.shortcutIntentForAccount(this, accountId.toString())
        intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
        startActivity(intent)
    }

    private fun refreshAccount(accountId: AccountId) {
        Toast.makeText(this, R.string.gnofle_accounts_refreshing, Toast.LENGTH_SHORT).show()
        startCheckMail(accountId)
    }

    private fun refreshAll(accountIds: List<AccountId>) {
        if (accountIds.isEmpty()) return

        Toast.makeText(this, R.string.gnofle_accounts_refreshing, Toast.LENGTH_SHORT).show()
        refreshAllPending = refreshAllPending + accountIds
        accountIds.forEach { startCheckMail(it) }
    }

    private fun startCheckMail(accountId: AccountId) {
        changeSyncCount(accountId, +1)
        try {
            messagingController.checkMail(accountId, true, true, false, checkMailListener)
        } catch (e: Exception) {
            onCheckMailFinished(accountId)
        }
    }

    private fun onCheckMailFinished(accountId: AccountId) {
        changeSyncCount(accountId, -1)
        if (syncCounts[accountId] == null) {
            refreshAllPending = refreshAllPending - accountId
        }
    }

    private fun changeSyncCount(accountId: AccountId, delta: Int) {
        val updated = ((syncCounts[accountId] ?: 0) + delta).coerceAtLeast(0)
        syncCounts = if (updated == 0) syncCounts - accountId else syncCounts + (accountId to updated)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAccountItems(): Flow<List<AccountListItem>> {
        return accountManager.observeAll().flatMapLatest { allAccounts ->
            val accounts = allAccounts.filter { it.isFinishedSetup }
            if (accounts.isEmpty()) {
                flowOf(emptyList())
            } else {
                val countFlows: List<Flow<Int>> = accounts.map { unreadCountFlow(it) }
                combine(countFlows) { counts ->
                    accounts.mapIndexed { index, account ->
                        AccountListItem(
                            id = account.id,
                            name = account.profile.name,
                            lastSyncTime = account.lastSyncTime,
                            color = account.profile.color,
                            unreadCount = counts[index],
                        )
                    }
                }
            }
        }
    }

    private fun unreadCountFlow(account: LegacyAccount): Flow<Int> {
        val inboxFolderId = account.inboxFolderId ?: return flowOf(0)
        val search = LocalMessageSearch().apply {
            addAccountId(account.id)
            addAllowedFolder(inboxFolderId)
        }
        return messageCountsProvider.getMessageCountsFlow(search).map { it.unread }
    }

    companion object {
        private const val UI_PREFERENCES = "gnoflemail_ui"
        private const val KEY_NAME_FONT_SIZE = "account_list_name_font_size"
        private const val MIN_NAME_FONT_SIZE = 10
        private const val MAX_NAME_FONT_SIZE = 20
        private const val DEFAULT_NAME_FONT_SIZE = 16

        @JvmStatic
        fun launch(activity: Activity) {
            activity.startActivity(Intent(activity, AccountListActivity::class.java))
        }
    }
}

@Composable
private fun AccountListScreen(
    logoResId: Int,
    appName: String,
    items: List<AccountListItem>,
    nameFontSize: Int,
    syncingAccountIds: Set<AccountId>,
    refreshingAll: Boolean,
    onAccountClick: (AccountId) -> Unit,
    onAccountRefresh: (AccountId) -> Unit,
    onRefreshAll: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground),
    ) {
        Header(
            logoResId = logoResId,
            appName = appName,
            refreshing = refreshingAll,
            onRefreshAll = onRefreshAll,
            onSettingsClick = onSettingsClick,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
        ) {
            BasicText(
                text = stringResource(R.string.gnofle_accounts_title),
                style = TextStyle(fontSize = 20.sp, color = TextGrey),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )

            items.forEach { item ->
                AccountRow(
                    item = item,
                    nameFontSize = nameFontSize,
                    syncing = item.id in syncingAccountIds,
                    onClick = { onAccountClick(item.id) },
                    onRefresh = { onAccountRefresh(item.id) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(
    logoResId: Int,
    appName: String,
    refreshing: Boolean,
    onRefreshAll: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBlue)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(logoResId),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        BasicText(
            text = appName,
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onRefreshAll),
            contentAlignment = Alignment.Center,
        ) {
            SpinningRefreshIcon(spinning = refreshing, tint = Color.White, iconSize = 28.dp)
        }
        HeaderIcon(
            iconResId = R.drawable.ic_gnofle_settings,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun HeaderIcon(iconResId: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconResId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun AccountRow(
    item: AccountListItem,
    nameFontSize: Int,
    syncing: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val lastSyncText = if (item.lastSyncTime > 0) {
        DateUtils.formatDateTime(
            context,
            item.lastSyncTime,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                DateUtils.FORMAT_NUMERIC_DATE or DateUtils.FORMAT_SHOW_YEAR,
        )
    } else {
        stringResource(R.string.gnofle_accounts_never_synced)
    }
    val shape = RectangleShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .shadow(elevation = 2.dp, shape = shape)
            .background(Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            SpinningRefreshIcon(
                spinning = syncing,
                tint = IconGrey,
                iconSize = 20.dp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = nameFontSize.sp, color = TextDark),
            )
            BasicText(
                text = lastSyncText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = (nameFontSize * 0.75f - 1f).coerceAtLeast(6f).sp, color = TextGrey),
            )
        }

        if (item.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF000000.toInt() or item.color), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = item.unreadCount.toString(),
                    style = TextStyle(
                        fontSize = (nameFontSize * 1.03125f).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
            }
        }

        Image(
            painter = painterResource(R.drawable.ic_gnofle_chevron),
            contentDescription = null,
            colorFilter = ColorFilter.tint(IconGrey),
            modifier = Modifier
                .padding(start = 4.dp)
                .size(22.dp),
        )
    }
}


/**
 * Refresh arrow that keeps turning while [spinning] is true. When it becomes false the arrow finishes the
 * turn it is on and stops, so it always makes at least one full turn.
 */
@Composable
private fun SpinningRefreshIcon(spinning: Boolean, tint: Color, iconSize: Dp) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing),
                )
            }
        } else {
            val target = ceil(rotation.value / 360f) * 360f
            if (target > rotation.value) {
                val fraction = (target - rotation.value) / 360f
                rotation.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = (900 * fraction).toInt().coerceAtLeast(1),
                        easing = LinearEasing,
                    ),
                )
            }
            rotation.snapTo(0f)
        }
    }

    Image(
        painter = painterResource(R.drawable.ic_gnofle_refresh),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier
            .size(iconSize)
            .graphicsLayer { rotationZ = rotation.value % 360f },
    )
}
