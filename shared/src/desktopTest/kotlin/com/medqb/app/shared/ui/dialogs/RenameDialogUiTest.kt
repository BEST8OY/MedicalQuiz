package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RenameDialogUiTest {

    @Test
    fun compactLandscapeKeepsSaveAndCancelActionsDisplayed() = runComposeUiTest {
        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(640.dp, 320.dp)) {
                RenameDialog(
                    currentName = "Cardiology Session",
                    onNameChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        onNodeWithText("Rename entry").assertIsDisplayed()
        onNodeWithText("Save").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
    }
}
