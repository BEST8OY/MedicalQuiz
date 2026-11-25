package com.medqb.app.shared.ui.dialogs.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.medqb.app.shared.ui.adaptive.ProvideAdaptiveLayoutInfo
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class AdaptiveSheetComponentsUiTest {

    @Composable
    private fun SampleAdaptiveSheet() {
        MedQBModalBottomSheet(onDismissRequest = {}) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Adaptive Sheet Content")
            }
        }
    }

    @Test
    fun phoneLandscapeUsesModalBottomSheetWithDragHandleInsteadOfDialog() = runComposeUiTest {
        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(900.dp, 410.dp)) {
                SampleAdaptiveSheet()
            }
        }

        onNodeWithText("Adaptive Sheet Content").assertIsDisplayed()
        onNodeWithTag("modal_sheet_drag_handle", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("modal_sheet_dialog").assertDoesNotExist()
    }

    @Test
    fun expandedViewportWithNonCompactHeightPromotesSheetToDialog() = runComposeUiTest {
        setContent {
            ProvideAdaptiveLayoutInfo(modifier = Modifier.requiredSize(900.dp, 600.dp)) {
                SampleAdaptiveSheet()
            }
        }

        onNodeWithText("Adaptive Sheet Content").assertIsDisplayed()
        onNodeWithTag("modal_sheet_dialog").assertIsDisplayed()
        onNodeWithTag("modal_sheet_drag_handle", useUnmergedTree = true).assertDoesNotExist()
    }
}
