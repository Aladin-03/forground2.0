package com.aladin.foreground2.feature.onboarding.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.core.permissions.Permission
import com.aladin.foreground2.core.permissions.openAutostartSettings
import com.aladin.foreground2.core.permissions.openSettingsFor
import com.aladin.foreground2.feature.onboarding.OnboardingUiState
import com.aladin.foreground2.feature.onboarding.OnboardingViewModel
import com.aladin.foreground2.feature.onboarding.PermissionStatus

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    val notificationRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.refresh()
            // No rationale after a denial means the system won't show the dialog again: send the user to Settings.
            val blocked =
                activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
            if (!granted && blocked) context.openSettingsFor(Permission.Notifications)
        }

    OnboardingContent(
        state = state,
        onGrant = { permission ->
            if (permission == Permission.Notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openSettingsFor(permission)
            }
        },
        onOpenAutostart = { context.openAutostartSettings() },
        onFinish = viewModel::onFinish,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onGrant: (Permission) -> Unit,
    onOpenAutostart: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ForegroundTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.permissions.forEach { status ->
            PermissionCard(status = status, onGrant = { onGrant(status.permission) })
        }
        AutostartCard(onOpen = onOpenAutostart)
        Button(
            onClick = onFinish,
            enabled = state.canFinish,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
        if (!state.canFinish) {
            Text(
                text = stringResource(R.string.onboarding_continue_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    status: PermissionStatus,
    onGrant: () -> Unit,
) {
    val text = status.permission.text()
    StepCard(
        title = stringResource(text.title),
        tag =
            stringResource(
                if (status.permission.required) R.string.onboarding_required else R.string.onboarding_recommended,
            ),
        body = stringResource(text.body),
    ) {
        Text(
            text = stringResource(if (status.granted) R.string.onboarding_granted else R.string.onboarding_not_granted),
            style = MaterialTheme.typography.labelLarge,
            color = if (status.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (!status.granted) {
            OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.onboarding_grant)) }
        }
    }
}

@Composable
private fun AutostartCard(onOpen: () -> Unit) {
    StepCard(
        title = stringResource(R.string.onboarding_autostart_title),
        tag = stringResource(R.string.onboarding_recommended),
        body = stringResource(R.string.onboarding_autostart_body),
    ) {
        Text(
            text = stringResource(R.string.onboarding_autostart_unchecked),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onOpen) { Text(stringResource(R.string.onboarding_autostart_open)) }
    }
}

@Composable
private fun StepCard(
    title: String,
    tag: String,
    body: String,
    footer: @Composable RowScope.() -> Unit,
) {
    val spacing = ForegroundTheme.spacing
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = footer)
        }
    }
}

private class PermissionText(
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private fun Permission.text(): PermissionText =
    when (this) {
        Permission.UsageAccess -> PermissionText(R.string.permission_usage_title, R.string.permission_usage_body)
        Permission.Overlay -> PermissionText(R.string.permission_overlay_title, R.string.permission_overlay_body)
        Permission.Notifications ->
            PermissionText(R.string.permission_notifications_title, R.string.permission_notifications_body)
        Permission.BatteryOptimization ->
            PermissionText(R.string.permission_battery_title, R.string.permission_battery_body)
    }

@Preview(showBackground = true)
@Composable
private fun OnboardingContentPreview() {
    ForegroundTheme {
        OnboardingContent(
            state =
                OnboardingUiState(
                    permissions =
                        listOf(
                            PermissionStatus(Permission.UsageAccess, granted = true),
                            PermissionStatus(Permission.Overlay, granted = false),
                            PermissionStatus(Permission.Notifications, granted = false),
                            PermissionStatus(Permission.BatteryOptimization, granted = false),
                        ),
                ),
            onGrant = {},
            onOpenAutostart = {},
            onFinish = {},
        )
    }
}
