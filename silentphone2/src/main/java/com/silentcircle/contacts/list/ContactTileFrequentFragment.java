/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.silentcircle.contacts.list;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.silentphone2.R;

/**
 * Fragment containing a list of frequently contacted people.
 */
public class ContactTileFrequentFragment extends ContactTileListFragment {
    private static final String TAG = ContactTileFrequentFragment.class.getSimpleName();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View listLayout = inflateAndSetupView(inflater, container, savedInstanceState, R.layout.contact_tile_list_frequent);
        View headerView = ContactsUtils.createHeaderView(getActivity(), R.string.favoritesFrequentContacted);
        ViewGroup headerContainer = (ViewGroup) listLayout.findViewById(R.id.header_container);
        headerContainer.addView(headerView);
        return listLayout;
    }
}
