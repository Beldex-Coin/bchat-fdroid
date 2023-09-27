package com.thoughtcrimes.securesms.conversation.v2.input_bar

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import com.beldex.libbchat.messaging.sending_receiving.link_preview.LinkPreview
import io.beldex.bchat.R
import io.beldex.bchat.databinding.ViewInputBarBinding
import com.beldex.libbchat.utilities.TextSecurePreferences
import com.beldex.libbchat.utilities.recipients.Recipient
import com.thoughtcrimes.securesms.conversation.v2.components.LinkPreviewDraftView
import com.thoughtcrimes.securesms.conversation.v2.components.LinkPreviewDraftViewDelegate
import com.thoughtcrimes.securesms.conversation.v2.messages.QuoteView
import com.thoughtcrimes.securesms.conversation.v2.messages.QuoteViewDelegate
import com.thoughtcrimes.securesms.database.model.MessageRecord
import com.thoughtcrimes.securesms.database.model.MmsMessageRecord
import com.thoughtcrimes.securesms.util.toDp
import com.thoughtcrimes.securesms.util.toPx
import com.thoughtcrimes.securesms.mms.GlideRequests
import com.thoughtcrimes.securesms.util.getColorWithID


class InputBar : RelativeLayout, InputBarEditTextDelegate, QuoteViewDelegate, LinkPreviewDraftViewDelegate{
    private lateinit var binding: ViewInputBarBinding
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val vMargin by lazy { toDp(4, resources) }
    private val minHeight by lazy { toPx(56, resources) }
    private var linkPreviewDraftView: LinkPreviewDraftView? = null
    var delegate: InputBarDelegate? = null
    var additionalContentHeight = 0
    var quote: MessageRecord? = null
    var linkPreview: LinkPreview? = null
    var showInput: Boolean = true
        set(value) { field = value; showOrHideInputIfNeeded() }

    var showMediaControls: Boolean = true
        set(value) {
            field = value
            showOrHideMediaControlsIfNeeded()
            binding.inputBarEditText.showMediaControls = value
        }
    var text: String
        get() { return binding.inputBarEditText.text?.toString() ?: "" }
        set(value) { binding.inputBarEditText.setText(value) }

    val attachmentButtonsContainerHeight: Int
        get() = binding.attachmentsButtonContainer.height

    private var mLastClickTime: Long = 0
    private var inChatBDXButtonLastClickTime: Long = 0
    private var inChatBDXButtonLastLongClickTime: Long = 0
    private var sendButtonLastClickTime: Long = 0
    private var microPhoneButtonLastLongClickTime: Long = 0

    private val attachmentsButton by lazy { InputBarButton(context, R.drawable.ic_attach, isMessageBox = true) }
    private val microphoneButton by lazy { InputBarButton(context, R.drawable.ic_microphone, isMessageBox = true) }
    private val sendButton by lazy { InputBarButton(context, R.drawable.ic_send, true, isMessageBox = true) }

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewInputBarBinding.inflate(LayoutInflater.from(context), this, true)
        // Attachments button
        binding.attachmentsButtonContainer.addView(attachmentsButton)
        attachmentsButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        attachmentsButton.isMotionEventSplittingEnabled = false
        attachmentsButton.onPress = {
            if (SystemClock.elapsedRealtime() - mLastClickTime >= 500){
                mLastClickTime = SystemClock.elapsedRealtime()
                toggleAttachmentOptions()
            }
        }
        // Microphone button
        binding.microphoneOrSendButtonContainer.addView(microphoneButton)
        microphoneButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        microphoneButton.onLongPress = {
            if (SystemClock.elapsedRealtime() - microPhoneButtonLastLongClickTime >= 1000 && SystemClock.elapsedRealtime() - sendButtonLastClickTime >= 1000){
                microPhoneButtonLastLongClickTime = SystemClock.elapsedRealtime()
                startRecordingVoiceMessage()
            }
        }
        microphoneButton.onMove = { delegate?.onMicrophoneButtonMove(it) }
        microphoneButton.onCancel = { delegate?.onMicrophoneButtonCancel(it) }
        microphoneButton.onUp = { delegate?.onMicrophoneButtonUp(it) }
        // Send button
        binding.microphoneOrSendButtonContainer.addView(sendButton)
        sendButton.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        sendButton.isVisible = false
        sendButton.onUp = {
            if (SystemClock.elapsedRealtime() - sendButtonLastClickTime >= 500){
                sendButtonLastClickTime = SystemClock.elapsedRealtime()
                delegate?.sendMessage()
            }
        }
        // Edit text
        binding.inputBarEditText.delegate = this
        // In Chat BDX
        binding.inChatBDX.setOnClickListener {
            if (SystemClock.elapsedRealtime() - inChatBDXButtonLastClickTime >= 500){
                inChatBDXButtonLastClickTime = SystemClock.elapsedRealtime()
                delegate?.walletDetailsUI()
            }
        }

        binding.inChatBDX.setOnLongClickListener {
            if (SystemClock.elapsedRealtime() - inChatBDXButtonLastLongClickTime >= 500){
                inChatBDXButtonLastLongClickTime = SystemClock.elapsedRealtime()
                delegate?.inChatBDXOptions()
            }
            true
        }



        /* Hales63 */
        val incognitoFlag = if (TextSecurePreferences.isIncognitoKeyboardEnabled(context)) 16777216 else 0
        binding.inputBarEditText.imeOptions = binding.inputBarEditText.imeOptions or incognitoFlag
        if(TextSecurePreferences.isEnterSendsEnabled(context))
        {
            //Log.d("Beldex","is enter send enable if ${TextSecurePreferences.isEnterSendsEnabled(context)}")
            binding.inputBarEditText.inputType = (EditorInfo.TYPE_CLASS_TEXT)
            binding.inputBarEditText.imeOptions = (EditorInfo.IME_ACTION_SEND);
            binding.inputBarEditText.setOnKeyListener(OnKeyListener { v, keyCode, event -> // If the event is a key-down event on the "enter" button
                if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_ENTER
                ) {
                    // Perform action on key press
                    delegate?.sendMessage()
                    binding.inputBarEditText.requestFocus()
                    return@OnKeyListener true
                }
                false
            })
        }
    }
    // endregion

    // region Updating
    override fun inputBarEditTextContentChanged(text: CharSequence) {
        sendButton.isVisible = text.isNotEmpty() && text.isNotBlank()
        microphoneButton.isVisible = text.isEmpty() || text.isBlank()
        delegate?.inputBarEditTextContentChanged(text)
    }

    override fun inputBarEditTextHeightChanged(newValue: Int) {
    }

    override fun commitInputContent(contentUri: Uri) {
        delegate?.commitInputContent(contentUri)
    }

    private fun toggleAttachmentOptions() {
        delegate?.toggleAttachmentOptions()
    }

    private fun startRecordingVoiceMessage() {
            delegate?.startRecordingVoiceMessage()
    }

    // Drafting quotes and drafting link previews is mutually exclusive, i.e. you can't draft
    // a quote and a link preview at the same time.

    fun draftQuote(thread: Recipient, message: MessageRecord, glide: GlideRequests) {
        quote = message
        linkPreview = null
        linkPreviewDraftView = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        // inflate quoteview with typed array here
        val layout = LayoutInflater.from(context).inflate(R.layout.view_quote_draft, binding.inputBarAdditionalContentContainer, false)
        val quoteView = layout.findViewById<QuoteView>(R.id.mainQuoteViewContainer)
        quoteView.delegate = this
        binding.inputBarAdditionalContentContainer.addView(layout)
        val attachments = (message as? MmsMessageRecord)?.slideDeck
        val sender = if (message.isOutgoing) TextSecurePreferences.getLocalNumber(context)!! else message.individualRecipient.address.serialize()
        quoteView.bind(sender, message.body, attachments,
            thread, true, message.isOpenGroupInvitation, message.isPayment,message.isOutgoing,message.threadId, false, glide)
        requestLayout()
    }

    override fun cancelQuoteDraft(i:Int) {
        quote = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        requestLayout()
        if(i==1){
            binding.inputBarEditText.text?.clear()
        }
    }

    fun draftLinkPreview() {
        quote = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        val linkPreviewDraftView = LinkPreviewDraftView(context)
        linkPreviewDraftView.delegate = this
        this.linkPreviewDraftView = linkPreviewDraftView
        binding.inputBarAdditionalContentContainer.addView(linkPreviewDraftView)
        requestLayout()
    }

    fun updateLinkPreviewDraft(glide: GlideRequests, linkPreview: LinkPreview) {
        this.linkPreview = linkPreview
        val linkPreviewDraftView = this.linkPreviewDraftView ?: return
        linkPreviewDraftView.update(glide, linkPreview)
    }

    override fun cancelLinkPreviewDraft(i:Int) {
        if (quote != null) { return }
        linkPreview = null
        binding.inputBarAdditionalContentContainer.removeAllViews()
        requestLayout()
        if(i==1){
            binding.inputBarEditText.text?.clear()
        }
    }

    private fun showOrHideInputIfNeeded() {
        if (showInput) {
            setOf( binding.inputBarEditText, attachmentsButton ).forEach { it.isVisible = true }
            microphoneButton.isVisible = text.isEmpty() || text.isBlank()
            sendButton.isVisible = text.isNotEmpty() && text.isNotBlank()
            binding.noLongerParticipantTextView.isVisible = false
        } else {
            cancelQuoteDraft(2)
            cancelLinkPreviewDraft(2)
            val views = setOf( binding.inputBarEditText, attachmentsButton, microphoneButton, sendButton )
            views.forEach { it.isVisible = false }
            binding.noLongerParticipantTextView.isVisible = true
        }
    }
    /*Hales63*/
    private fun showOrHideMediaControlsIfNeeded() {
        setOf(attachmentsButton, microphoneButton).forEach { it.snIsEnabled = showMediaControls }
    }

    fun addTextChangedListener(textWatcher: TextWatcher) {
        binding.inputBarEditText.addTextChangedListener(textWatcher)
    }

    fun setSelection(index: Int) {
        binding.inputBarEditText.setSelection(index)
    }

    //Payment Tag

    fun setTextColor(thread: Recipient?, reportIssueId: String, status: Boolean) {
        if (!thread?.isGroupRecipient!! && thread.hasApprovedMe() && !thread.isBlocked && reportIssueId != thread.address.toString() && !thread.isLocalNumber) {
            if (status) {
                val face = Typeface.createFromAsset(context!!.assets,
                    "fonts/open_sans_bold.ttf")
                binding.inputBarEditText.setTextColor(context.resources.getColorWithID(R.color.button_green,
                    context.theme))
                binding.inputBarEditText.typeface = face
            } else {
                setEditTextStyleNormal()
            }
        } else {
            setEditTextStyleNormal()
        }
    }

    fun showPayAsYouChatBDXIcon(thread: Recipient,reportIssueId:String) {
        if (!thread.isGroupRecipient && thread.hasApprovedMe() && !thread.isBlocked && reportIssueId!=thread.address.toString() && !thread.isLocalNumber) {
            binding.payAsYouChatLayout.visibility = View.VISIBLE
        }else{
            binding.payAsYouChatLayout.visibility = View.GONE
        }
    }

    fun showProgressBar(status:Boolean){
        binding.blockProgressBar.isVisible = status
    }

    fun showFailedProgressBar(status: Boolean){
        binding.failedBlockProgressBar.isVisible = status
        binding.failedBlockProgressBar.progress = 0
    }

    fun setProgress(progress:Int){
        binding.blockProgressBar.progress = progress
    }

    fun setDrawableProgressBar(context: Context, type: Boolean,syncStatus: String) {
        if(TextSecurePreferences.isPayAsYouChat(context)) {
            if (type) {
                binding.failedBlockProgressBar.isVisible = type
                binding.failedBlockProgressBar.progress = 100
                binding.blockProgressBar.isVisible = !type
                binding.blockProgressBar.progress = 0
            } else {
                binding.blockProgressBar.isVisible = !type
                if(syncStatus == "100%" && syncStatus != "--") {
                    binding.blockProgressBar.progress = 100
                }
                binding.failedBlockProgressBar.isVisible = type
                binding.failedBlockProgressBar.progress = 0
            }
        }else{
            binding.failedBlockProgressBar.isVisible = false
            binding.failedBlockProgressBar.progress = 0
        }
    }
    fun showDrawableProgressBar(type: Boolean,syncStatus:String) {
        if (type) {
            binding.failedBlockProgressBar.isVisible = type
            binding.failedBlockProgressBar.progress = 100
            binding.blockProgressBar.isVisible = !type
            binding.blockProgressBar.progress = 0
        } else {
            binding.blockProgressBar.isVisible = !type
            if (syncStatus == "100%" && syncStatus != "--") {
                binding.blockProgressBar.progress = 100
            }
            binding.failedBlockProgressBar.isVisible = type
            binding.failedBlockProgressBar.progress = 0
        }
    }

    private fun setEditTextStyleNormal(){
        val face = Typeface.createFromAsset(context!!.assets,
            "fonts/open_sans_medium.ttf")
        binding.inputBarEditText.setTextColor(context.resources.getColorWithID(R.color.text,
            context.theme))
        binding.inputBarEditText.typeface = face
    }
    // endregion
}

interface InputBarDelegate {

    fun inputBarHeightChanged(newValue: Int)
    fun inputBarEditTextContentChanged(newContent: CharSequence)
    fun toggleAttachmentOptions()
    fun showVoiceMessageUI()
    fun startRecordingVoiceMessage()
    fun onMicrophoneButtonMove(event: MotionEvent)
    fun onMicrophoneButtonCancel(event: MotionEvent)
    fun onMicrophoneButtonUp(event: MotionEvent)
    fun sendMessage()
    fun sendBDX()   //Payment Tag
    fun commitInputContent(contentUri: Uri)
    fun inChatBDXOptions()
    fun walletDetailsUI()
}