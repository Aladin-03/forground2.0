package com.aladin.foreground2.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.aladin.foreground2.R
import com.aladin.foreground2.core.designsystem.ForegroundTheme
import com.aladin.foreground2.feature.home.HomeViewModel

@Composable
fun HomeScreen(
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UnusedParameter") viewModel: HomeViewModel = hiltViewModel(),
) {
    HomeContent(onOpenPicker = onOpenPicker, modifier = modifier)
}

@Composable
private fun HomeContent(
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ForegroundTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ForegroundTheme.spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Button(onClick = onOpenPicker) {
            Text(stringResource(R.string.home_open_picker))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    ForegroundTheme { HomeContent(onOpenPicker = {}) }
}
