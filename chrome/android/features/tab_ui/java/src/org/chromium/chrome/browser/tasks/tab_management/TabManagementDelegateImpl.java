// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import static org.chromium.chrome.browser.tasks.tab_management.TabManagementModuleProvider.SYNTHETIC_TRIAL_POSTFIX;

import android.view.ViewGroup;

import org.chromium.base.annotations.UsedByReflection;
import org.chromium.chrome.browser.ChromeActivity;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.metrics.UmaSessionStats;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestions;
import org.chromium.chrome.browser.tasks.tab_management.suggestions.TabSuggestionsOrchestrator;

/**
 * Impl class that will resolve components for tab management.
 */
@UsedByReflection("TabManagementModule")
public class TabManagementDelegateImpl implements TabManagementDelegate {
    @Override
    public GridTabSwitcher createGridTabSwitcher(ChromeActivity activity) {
        if (ChromeFeatureList.isInitialized()) {
            UmaSessionStats.registerSyntheticFieldTrial(
                    ChromeFeatureList.TAB_GRID_LAYOUT_ANDROID + SYNTHETIC_TRIAL_POSTFIX,
                    "Downloaded_Enabled");
        }
        return new GridTabSwitcherCoordinator(activity, activity.getLifecycleDispatcher(),
                activity.getToolbarManager(), activity.getTabModelSelector(),
                activity.getTabContentManager(), activity.getCompositorViewHolder(),
                activity.getFullscreenManager(), activity, activity::onBackPressed, activity);
    }

    @Override
    public TabGroupUi createTabGroupUi(
            ViewGroup parentView, ThemeColorProvider themeColorProvider) {
        return new TabGroupUiCoordinator(parentView, themeColorProvider);
    }

    @Override
    public TabSuggestions createTabSuggestions(ChromeActivity activity) {
        return TabSuggestionsOrchestrator.getInstance(activity.getTabModelSelector());
    }

    @Override
    public TabSuggestionEditorLayout createTabSuggestionEditorLayout(ChromeActivity activity) {
        return new TabSuggestionEditorCoordinator(activity, activity.getCompositorViewHolder(),
                activity.getTabModelSelector(), activity.getTabContentManager(), activity);
    }
}
