package com.medqb.app.shared.ui.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import kotlin.test.Test
import kotlin.test.assertTrue

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

    @Test
    fun clickingOutsideDialogDismissesIt() = runComposeUiTest {
        var dismissed = false
        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(800.dp, 600.dp)) {
                    RenameDialog(
                        currentName = "Cardiology Session",
                        onNameChange = {},
                        onConfirm = {},
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        onNodeWithText("Rename entry").assertIsDisplayed()
        onAllNodes(isRoot(), useUnmergedTree = true)[1].performTouchInput {
            click(Offset(5f, 5f))
        }
        waitForIdle()
        assertTrue(dismissed, "Clicking outside DialogShell should invoke onDismiss")
    }
}
