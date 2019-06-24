// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.tasks.tab_management.suggestions;

import org.chromium.base.Callback;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements {@link TabSuggestionsFetcher}. Abstracts the details of
 * communicating with all known client-side {@link TabSuggestionProvider}
 */
public final class TabSuggestionsClientFetcher implements TabSuggestionsFetcher {
    private List<TabSuggestionProvider> mClientSuggestionProviders;

    public TabSuggestionsClientFetcher(TabModelSelector selector) {
        mClientSuggestionProviders = new ArrayList<>(Arrays.asList(new StaleTabSuggestionProvider(),
                new DuplicatePageTabSuggestionProvider(),
                new SessionTabSwitchesSuggestionProvider(selector)));
    }

    @Override
    public void fetch(TabContext tabContext, Callback<TabSuggestionsFetcherResults> callback) {
        List<TabSuggestion> retList = new ArrayList<>();

        for (TabSuggestionProvider provider : mClientSuggestionProviders) {
            List<TabSuggestion> suggestions = provider.suggest(tabContext);
            android.util.Log.e("TabSuggestionsDetailed","Asking client side provider");
            if (suggestions != null && !suggestions.isEmpty()) {
                android.util.Log.e("TabSuggestionsDetailed","Got some from "+suggestions.get(0).getProviderName());
                retList.addAll(suggestions);
            }
        }
        callback.onResult(new TabSuggestionsFetcherResults(retList, tabContext));
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
