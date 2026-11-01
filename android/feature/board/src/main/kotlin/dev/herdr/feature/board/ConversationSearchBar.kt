package dev.herdr.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.herdr.feature.board.design.LocalHerdrPalette
import dev.herdr.feature.board.design.Space
import dev.herdr.feature.board.design.TouchTargetIconButton

@Composable
internal fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    cursor: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalHerdrPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.surface)
            .padding(horizontal = Space.page, vertical = Space.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search_placeholder), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(Space.s))
            Text(
                if (query.isBlank()) "" else if (matchCount == 0) stringResource(R.string.search_no_result) else "${cursor + 1}/$matchCount",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            TouchTargetIconButton(
                iconRes = R.drawable.ic_collapsed,
                contentDescription = stringResource(R.string.search_prev),
                onClick = onPrev,
            )
            TouchTargetIconButton(
                iconRes = R.drawable.ic_chevron_right,
                contentDescription = stringResource(R.string.search_next),
                onClick = onNext,
            )
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.search_close), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            stringResource(R.string.search_scope),
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
        )
    }
}
