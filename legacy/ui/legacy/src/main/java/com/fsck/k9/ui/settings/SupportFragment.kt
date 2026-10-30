package com.fsck.k9.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.core.common.provider.AppNameProvider
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

/**
 * "Support Gnoflemail" screen: logo, short message and a clickable PayPal link at the bottom.
 */
class SupportFragment : Fragment() {
    private val themeProvider: FeatureThemeProvider by inject()
    private val appNameProvider: AppNameProvider by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val logoResId = resolveAppLogoResId(requireContext())
            val appName = appNameProvider.appName
            val text1 = context.getString(R.string.support_screen_text_1)
            val text2 = context.getString(R.string.support_screen_text_2)
            val text3 = context.getString(R.string.support_screen_text_3)
            val linkLabel = context.getString(R.string.support_screen_link_label)
            val linkUrl = context.getString(R.string.support_screen_link_url)

            setContent {
                themeProvider.WithTheme {
                    SupportScreen(
                        logoResId = logoResId,
                        appName = appName,
                        text1 = text1,
                        text2 = text2,
                        text3 = text3,
                        linkLabel = linkLabel,
                        onLinkClick = { openLink(linkUrl) },
                    )
                }
            }
        }
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SupportScreen(
    logoResId: Int,
    appName: String,
    text1: String,
    text2: String,
    text3: String,
    linkLabel: String,
    onLinkClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Image(
                painter = painterResource(logoResId),
                contentDescription = null,
                modifier = Modifier.size(250.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))
            BasicText(
                text = appName,
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B4A80),
                    textAlign = TextAlign.Center,
                ),
            )

            Spacer(modifier = Modifier.height(28.dp))
            TextBodyLarge(text = text1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(28.dp))
            TextBodyLarge(text = text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(28.dp))
            TextBodyLarge(text = text3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(32.dp))
            TextBodyLarge(
                text = linkLabel,
                color = Color(0xFF0057A8),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLinkClick)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
