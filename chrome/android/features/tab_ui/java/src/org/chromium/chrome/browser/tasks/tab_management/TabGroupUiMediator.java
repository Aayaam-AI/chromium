// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management;

import android.net.Uri;
import android.text.TextUtils;

import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.metrics.RecordUserAction;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ThemeColorProvider;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.compositor.layouts.EmptyOverviewModeObserver;
import org.chromium.chrome.browser.compositor.layouts.OverviewModeBehavior;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelObserver;
import org.chromium.chrome.browser.tabmodel.EmptyTabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabCreatorManager;
import org.chromium.chrome.browser.tabmodel.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorObserver;
import org.chromium.chrome.browser.tabmodel.TabModelSelectorTabObserver;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.chrome.browser.toolbar.bottom.BottomControlsCoordinator;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.ui.modelutil.PropertyModel;

import java.util.List;

/**
 * A mediator for the TabGroupUi. Responsible for managing the
 * internal state of the component.
 */
public class TabGroupUiMediator {
    /**
     * Defines an interface for a {@link TabGroupUiMediator} reset event
     * handler.
     */
    interface ResetHandler {
        /**
         * Handles a reset event originated from {@link TabGroupUiMediator}
         * when the bottom sheet is collapsed.
         *
         * @param tabs List of Tabs to reset.
         */
        void resetStripWithListOfTabs(List<Tab> tabs);

        /**
         * Handles a reset event originated from {@link TabGroupUiMediator}
         * when the bottom sheet is expanded.
         *
         * @param tabs List of Tabs to reset.
         */
        void resetSheetWithListOfTabs(List<Tab> tabs);
    }

    private final PropertyModel mToolbarPropertyModel;
    private final TabModelObserver mTabModelObserver;
    private final ResetHandler mResetHandler;
    private final TabModelSelector mTabModelSelector;
    private final TabCreatorManager mTabCreatorManager;
    private final OverviewModeBehavior mOverviewModeBehavior;
    private final OverviewModeBehavior.OverviewModeObserver mOverviewModeObserver;
    private final BottomControlsCoordinator
            .BottomControlsVisibilityController mVisibilityController;
    private final ThemeColorProvider mThemeColorProvider;
    private final ThemeColorProvider.ThemeColorObserver mThemeColorObserver;
    private final ThemeColorProvider.TintObserver mTintObserver;
    private final TabModelSelectorTabObserver mTabModelSelectorTabObserver;
    private final TabModelSelectorObserver mTabModelSelectorObserver;
    private boolean mIsTabGroupUiVisible;
    private boolean mIsClosingAGroup;

    TabGroupUiMediator(
            BottomControlsCoordinator.BottomControlsVisibilityController visibilityController,
            ResetHandler resetHandler, PropertyModel toolbarPropertyModel,
            TabModelSelector tabModelSelector, TabCreatorManager tabCreatorManager,
            OverviewModeBehavior overviewModeBehavior, ThemeColorProvider themeColorProvider) {
        mResetHandler = resetHandler;
        mToolbarPropertyModel = toolbarPropertyModel;
        mTabModelSelector = tabModelSelector;
        mTabCreatorManager = tabCreatorManager;
        mOverviewModeBehavior = overviewModeBehavior;
        mVisibilityController = visibilityController;
        mThemeColorProvider = themeColorProvider;

        // register for tab model
        mTabModelObserver = new EmptyTabModelObserver() {
            @Override
            public void didSelectTab(Tab tab, @TabSelectionType int type, int lastId) {
                if (!mIsTabGroupUiVisible) return;
                if (type == TabSelectionType.FROM_CLOSE && !mIsClosingAGroup) return;
                if (getRelatedTabsForId(lastId).contains(tab)) return;
                resetTabStripWithRelatedTabsForId(tab.getId());
            }

            @Override
            public void willCloseTab(Tab tab, boolean animate) {
                if (!mIsTabGroupUiVisible) return;
                List<Tab> group = mTabModelSelector.getTabModelFilterProvider()
                                          .getCurrentTabModelFilter()
                                          .getRelatedTabList(tab.getId());

                mIsClosingAGroup = group.size() == 0;
            }

            @Override
            public void didAddTab(Tab tab, int type) {
                if (type == TabLaunchType.FROM_RESTORE) return;
                resetTabStripWithRelatedTabsForId(tab.getId());
            }

            @Override
            public void restoreCompleted() {
                Tab currentTab = mTabModelSelector.getCurrentTab();
                resetTabStripWithRelatedTabsForId(currentTab.getId());
            }
        };
        mOverviewModeObserver = new EmptyOverviewModeObserver() {
            @Override
            public void onOverviewModeStartedShowing(boolean showToolbar) {
                resetTabStripWithRelatedTabsForId(Tab.INVALID_TAB_ID);
            }

            @Override
            public void onOverviewModeFinishedHiding() {
                Tab tab = mTabModelSelector.getCurrentTab();
                if (tab == null) return;
                resetTabStripWithRelatedTabsForId(tab.getId());
            }
        };

        mTabModelSelectorTabObserver = new TabModelSelectorTabObserver(mTabModelSelector) {
            @Override
            public void onPageLoadStarted(Tab tab, String url) {
                List<Tab> listOfTabs = mTabModelSelector.getTabModelFilterProvider()
                                               .getCurrentTabModelFilter()
                                               .getRelatedTabList(tab.getId());
                int numTabs = listOfTabs.size();
                // This is set to zero because the UI is hidden.
                if (!mIsTabGroupUiVisible) numTabs = 0;
                RecordHistogram.recordCountHistogram("TabStrip.TabCountOnPageLoad", numTabs);
            }
        };

        mTabModelSelectorObserver = new EmptyTabModelSelectorObserver() {
            @Override
            public void onTabModelSelected(TabModel newModel, TabModel oldModel) {
                if (newModel.isIncognito() && newModel.getCount() == 1) {
                    resetTabStripWithRelatedTabsForId(newModel.getTabAt(0).getId());
                }
            }
        };

        mThemeColorObserver = (color, shouldAnimate)
                -> mToolbarPropertyModel.set(TabStripToolbarViewProperties.PRIMARY_COLOR, color);
        mTintObserver = (tint,
                useLight) -> mToolbarPropertyModel.set(TabStripToolbarViewProperties.TINT, tint);

        mTabModelSelector.getTabModelFilterProvider().addTabModelFilterObserver(mTabModelObserver);
        mTabModelSelector.addObserver(mTabModelSelectorObserver);
        mOverviewModeBehavior.addOverviewModeObserver(mOverviewModeObserver);
        mThemeColorProvider.addThemeColorObserver(mThemeColorObserver);
        mThemeColorProvider.addTintObserver(mTintObserver);

        setupToolbarClickHandlers();
        mToolbarPropertyModel.set(TabStripToolbarViewProperties.IS_MAIN_CONTENT_VISIBLE, true);
        Tab tab = mTabModelSelector.getCurrentTab();
        if (tab != null) {
            resetTabStripWithRelatedTabsForId(tab.getId());
        }
    }

    private void setupToolbarClickHandlers() {
        mToolbarPropertyModel.set(TabStripToolbarViewProperties.EXPAND_CLICK_LISTENER, view -> {
            Tab currentTab = mTabModelSelector.getCurrentTab();
            if (currentTab == null) return;
            if (ChromeFeatureList.isInitialized()
                    && ChromeFeatureList.isEnabled(ChromeFeatureList.TAB_GROUP_STORIES)) {
                List<Tab> relatedTabs = mTabModelSelector.getTabModelFilterProvider()
                                                .getCurrentTabModelFilter()
                                                .getRelatedTabList(currentTab.getId());
                String param = "?urls=[";
                Tab storyTab = null;
                for (Tab tab : relatedTabs) {
                    if (tab.getUrl().startsWith(
                                "http://task-management-chrome.sandbox.google.com/stamp")) {
                        storyTab = tab;
                        continue;
                    }
                    Uri uri = Uri.parse(tab.getUrl());
                    String urlSansQuery = "%22" + uri.getScheme() + "://" + uri.getAuthority()
                            + uri.getEncodedPath() + "%22,";
                    param += urlSansQuery;
                }
                param = param.substring(0, param.length() - 1) + "]";
                LoadUrlParams urlParams = new LoadUrlParams(
                        "http://task-management-chrome.sandbox.google.com/stamp" + param);
                if (storyTab != null) {
                    storyTab.loadUrl(urlParams);
                    mTabModelSelector.getCurrentModel().setIndex(
                            mTabModelSelector.getCurrentModel().indexOf(storyTab),
                            TabSelectionType.FROM_USER);
                } else {
                    mTabCreatorManager.getTabCreator(false).createNewTab(
                            urlParams, TabLaunchType.FROM_CHROME_UI, currentTab);
                }
                return;
            }
            mResetHandler.resetSheetWithListOfTabs(getRelatedTabsForId(currentTab.getId()));
            RecordUserAction.record("TabGroup.ExpandedFromStrip");
        });
        mToolbarPropertyModel.set(TabStripToolbarViewProperties.ADD_CLICK_LISTENER, view -> {
            Tab currentTab = mTabModelSelector.getCurrentTab();
            List<Tab> relatedTabs = mTabModelSelector.getTabModelFilterProvider()
                                            .getCurrentTabModelFilter()
                                            .getRelatedTabList(currentTab.getId());

            assert relatedTabs.size() > 0;

            Tab parentTabToAttach = relatedTabs.get(relatedTabs.size() - 1);
            mTabCreatorManager.getTabCreator(currentTab.isIncognito())
                    .createNewTab(new LoadUrlParams(UrlConstants.NTP_URL),
                            TabLaunchType.FROM_CHROME_UI, parentTabToAttach);
            RecordUserAction.record("MobileNewTabOpened" + TabGroupUiCoordinator.COMPONENT_NAME);
        });
    }

    private void resetTabStripWithRelatedTabsForId(int id) {
        List<Tab> listOfTabs = mTabModelSelector.getTabModelFilterProvider()
                                       .getCurrentTabModelFilter()
                                       .getRelatedTabList(id);

        boolean shouldNotShowStrip = (listOfTabs.size() < 2)
                || (ChromeFeatureList.isInitialized()
                        && ChromeFeatureList.isEnabled(ChromeFeatureList.SHOPPING_ASSIST)
                        && (!TextUtils.isEmpty(ChromeFeatureList.getFieldTrialParamByFeature(
                                    ChromeFeatureList.SHOPPING_ASSIST, "shopping_assist_behavior"))
                                        && !ChromeFeatureList
                                                    .getFieldTrialParamByFeature(
                                                            ChromeFeatureList.SHOPPING_ASSIST,
                                                            "shopping_assist_behavior")
                                                    .startsWith("TabInAGroup")
                                || TextUtils.isEmpty(ChromeFeatureList.getFieldTrialParamByFeature(
                                        ChromeFeatureList.SHOPPING_ASSIST,
                                        "shopping_assist_behavior"))));
        if (shouldNotShowStrip) {
            mResetHandler.resetStripWithListOfTabs(null);
            mToolbarPropertyModel.set(TabStripToolbarViewProperties.IS_MAIN_CONTENT_VISIBLE, false);
            mIsTabGroupUiVisible = (ChromeFeatureList.isInitialized()
                    && (ChromeFeatureList.isEnabled(ChromeFeatureList.SHOPPING_ASSIST)
                            || ChromeFeatureList.isEnabled(ChromeFeatureList.TAB_GROUP_STORIES)));
        } else {
            mResetHandler.resetStripWithListOfTabs(listOfTabs);
            mToolbarPropertyModel.set(TabStripToolbarViewProperties.IS_MAIN_CONTENT_VISIBLE, true);
            mIsTabGroupUiVisible = true;
        }
        mVisibilityController.setBottomControlsVisible(mIsTabGroupUiVisible);
    }

    private List<Tab> getRelatedTabsForId(int id) {
        return mTabModelSelector.getTabModelFilterProvider()
                .getCurrentTabModelFilter()
                .getRelatedTabList(id);
    }

    public void destroy() {
        if (mTabModelObserver != null && mTabModelSelector != null) {
            mTabModelSelector.getTabModelFilterProvider().removeTabModelFilterObserver(
                    mTabModelObserver);
        }
        mOverviewModeBehavior.removeOverviewModeObserver(mOverviewModeObserver);
        mThemeColorProvider.removeThemeColorObserver(mThemeColorObserver);
        mThemeColorProvider.removeTintObserver(mTintObserver);
        mTabModelSelector.removeObserver(mTabModelSelectorObserver);
        mTabModelSelectorTabObserver.destroy();
    }
}
