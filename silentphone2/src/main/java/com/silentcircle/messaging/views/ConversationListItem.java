/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.silentcircle.messaging.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.MIME;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Widget to house information for single conversation in conversations view.
 */
public class ConversationListItem extends LinearLayout {

    /** Limit after which unread message count is displayed with greater than sign */
    public static final int UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT = 10;
    /**
     *
     *  Listener interface for {@link ConversationListItem} events.
     */
    public interface OnConversationItemClickListener {

        /* Callback for click event on main conversation list item body */
        void onConversationClick(final ConversationListItem view);

        /* Callback for click event on delete button */
        void onConversationDeleteClick(final ConversationListItem view);

        /* Callback for click event on conversation contact */
        void onConversationContactClick(final ConversationListItem view);
    };

    private OnConversationItemClickListener mOnConversationItemClickListener;

    private View.OnClickListener mConversationClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mOnConversationItemClickListener != null) {
                mOnConversationItemClickListener.onConversationClick(ConversationListItem.this);
            }
        }
    };

    private View.OnLongClickListener mConversationDeleteClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            boolean result = false;
            if (mOnConversationItemClickListener != null) {
                result = true;
                mOnConversationItemClickListener.onConversationDeleteClick(ConversationListItem.this);
            }
            return result;
        }
    };

    private View.OnClickListener mConversationContactClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mOnConversationItemClickListener != null) {
                mOnConversationItemClickListener.onConversationContactClick(ConversationListItem.this);
            }
        }
    };

    private View mMessageCardView;
    private TextView mMessageText;
    private TextView mNameView;
    private TextView mMessageTimeView;
    private TextView mStatusMessageCount;
    private QuickContactBadge mContactButton;
    private FrameLayout mStatusIcon;
    private View mConversationButton;

    public ConversationListItem(Context context) {
        this(context, null);
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textStyle);
    }

    public ConversationListItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(context, R.layout.messaging_log_list_item_new, this);

        mMessageText = (TextView) findViewById(R.id.message_text);
        mMessageCardView = findViewById(R.id.message_summary_card);
        mNameView = (TextView) findViewById(R.id.name);
        mContactButton =
                (QuickContactBadge) findViewById(R.id.quick_contact_photo);
        mMessageTimeView =
                (TextView) findViewById(R.id.message_time);
        mStatusIcon = (FrameLayout) findViewById(R.id.unread_message_notification);
        mStatusMessageCount = (TextView) findViewById(R.id.unread_message_count);
        mConversationButton = findViewById(R.id.conversation_action_view);
    }

    public void setOnConversationItemClickListener(final OnConversationItemClickListener listener) {
        mOnConversationItemClickListener = listener;
    }

    public void setConversation(final Conversation conversation) {
        ContactEntry contactEntry =
                ContactsCache.getContactEntryFromCache(conversation.getPartner().getUsername());

        long photoId = 0;
        Uri contactUri = null;
        String displayName = null;
        if (contactEntry != null) {
            photoId = contactEntry.photoId;
            contactUri = contactEntry.lookupUri;
            displayName = contactEntry.name;
        }

        if (TextUtils.isEmpty(displayName)) {
            displayName = conversation != null
                    ? conversation.getPartner().getUsername()
                    : getResources().getString(R.string.username_unknown);
        }

        mNameView.setText(displayName);
        setPhoto(mContactButton, photoId, contactUri, displayName, null,
                ContactPhotoManagerNew.TYPE_DEFAULT);

        if (conversation != null) {
            mMessageTimeView.setText(
                    android.text.format.DateUtils.getRelativeTimeSpanString(
                            conversation.getLastModified(),
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE));

            // if there are any unread messages in conversation, show status icon
            // TODO: wrap this in a widget?
            mStatusIcon.setVisibility(
                    conversation.containsUnreadMessages() ? View.VISIBLE : View.GONE);
            int unreadMessageCount = conversation.getUnreadMessageCount();
            mStatusMessageCount.setText(unreadMessageCount > UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                    ? ">" + UNREAD_MESSAGE_COUNT_DISPLAY_LIMIT
                    : String.valueOf(unreadMessageCount));
        }

        mConversationButton.setOnLongClickListener(mConversationDeleteClickListener);
        mConversationButton.setOnClickListener(mConversationClickListener);
        mContactButton.setOnClickListener(mConversationContactClickListener);
    }

    public void setMessageText(final String text) {
        mMessageText.setText(text);
    }

    public void setMessageBackground(final int drawable) {
        mMessageCardView.setBackgroundResource(drawable);
    }

    public void setEvent(final Event event) {
        String messageText = null;
        int backgroundResource = R.drawable.bg_empty_card_summary;
        if (event != null) {
            messageText = event.getText();
            if (event instanceof IncomingMessage) {
                backgroundResource = R.drawable.bg_card_summary;
                messageText = getAttachmentDescription((Message) event);
            } else if (event instanceof OutgoingMessage) {
                backgroundResource = R.drawable.bg_my_card_summary;
                messageText = getAttachmentDescription((Message) event);
            }
        }
        else {
            messageText = getResources().getString(R.string.messaging_conversation_list_no_messages);
        }
        setMessageText(messageText);
        setMessageBackground(backgroundResource);
    }

    private String getAttachmentDescription(Message message) {
        String result = message.getText();

        if (message.hasAttachment()) {
            Resources resources = getResources();
            int descriptionPrefixId = R.string.messaging_conversation_list_type_file;
            String attachmentMetaData = message.getMetaData();

            try {
                if (!TextUtils.isEmpty(attachmentMetaData)) {
                    JSONObject attachmentMetaDataJson = new JSONObject(attachmentMetaData);
                    String mimeType = attachmentMetaDataJson.getString("MimeType");
                    if (MIME.isPdf(mimeType)) {
                        descriptionPrefixId = R.string.messaging_conversation_list_type_pdf;
                    } else if (MIME.isAudio(mimeType)) {
                        descriptionPrefixId = R.string.messaging_conversation_list_type_audio;
                    } else if (MIME.isVideo(mimeType)) {
                        descriptionPrefixId = R.string.messaging_conversation_list_type_video;
                    } else if (MIME.isVisual(mimeType)) {
                        descriptionPrefixId = R.string.messaging_conversation_list_type_image;
                    } else if (MIME.isContact(mimeType)) {
                        descriptionPrefixId = R.string.messaging_conversation_list_type_contact;
                    }
                }
            } catch (JSONException exception) {
                // Leave descriptionPrefixId set to default value
            }
            String descriptionPrefix = resources.getString(descriptionPrefixId);

            int description = R.string.messaging_conversation_list_file_received;
            if (message instanceof OutgoingMessage) {
                description = R.string.messaging_conversation_list_file_sent;
            }

            result = resources.getString(description, descriptionPrefix);
        }

        return result;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setPhoto(QuickContactBadge view, long photoId, Uri contactUri,
            String displayName, String identifier, int contactType) {
        ContactPhotoManagerNew contactPhotoManager =
                ContactPhotoManagerNew.getInstance(getContext());
        view.assignContactUri(contactUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            view.setOverlay(null);
        ContactPhotoManagerNew.DefaultImageRequest request =
                new ContactPhotoManagerNew.DefaultImageRequest(displayName, identifier,
                        contactType, true /* isCircular */);
        contactPhotoManager.loadThumbnail(view, photoId, false /* darkTheme */,
                true /* isCircular */, request);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        mMessageCardView.setSelected(selected);
        mMessageText.setSelected(selected);
    }

}
