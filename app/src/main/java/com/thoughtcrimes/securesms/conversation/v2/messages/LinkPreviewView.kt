package com.thoughtcrimes.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import io.beldex.bchat.R
import io.beldex.bchat.databinding.ViewLinkPreviewBinding
import com.thoughtcrimes.securesms.components.CornerMask
import com.thoughtcrimes.securesms.conversation.v2.ModalUrlBottomSheet
import com.thoughtcrimes.securesms.conversation.v2.utilities.MessageBubbleUtilities
import com.thoughtcrimes.securesms.conversation.v2.utilities.TextUtilities.getIntersectedModalSpans
import com.thoughtcrimes.securesms.database.model.MmsMessageRecord
import com.thoughtcrimes.securesms.mms.GlideRequests
import com.thoughtcrimes.securesms.mms.ImageSlide
import com.thoughtcrimes.securesms.util.UiModeUtilities

class LinkPreviewView : LinearLayout {
    private lateinit var binding: ViewLinkPreviewBinding
    private val cornerMask by lazy {
        CornerMask(
            this
        )
    }
    private var url: String? = null
    lateinit var bodyTextView: TextView

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewLinkPreviewBinding.inflate(LayoutInflater.from(context), this, true)
    }
    // endregion

    // region Updating
    fun bind(
        message: MmsMessageRecord,
        glide: GlideRequests,
        isStartOfMessageCluster: Boolean,
        isEndOfMessageCluster: Boolean
    ) {
        val linkPreview = message.linkPreviews.first()
        url = linkPreview.url
        // Thumbnail
        if (linkPreview.getThumbnail().isPresent) {
            // This internally fetches the thumbnail
            binding.thumbnailImageView.setImageResource(glide,
                ImageSlide(
                    context,
                    linkPreview.getThumbnail().get()
                ), isPreview = false, message)
            binding.thumbnailImageView.loadIndicator.isVisible = false
        }
        // Title
        binding.titleTextView.text = linkPreview.title
        val textColorID = if (message.isOutgoing && UiModeUtilities.isDayUiMode(context)) {
            R.color.white
        } else {
            if (UiModeUtilities.isDayUiMode(context)) R.color.black else R.color.white
        }
        binding.titleTextView.setTextColor(ResourcesCompat.getColor(resources, textColorID, context.theme))
        // Body
        binding.titleTextView.setTextColor(ResourcesCompat.getColor(resources, textColorID, context.theme))
        // Corner radii
        val cornerRadii = MessageBubbleUtilities.calculateRadii(context, isStartOfMessageCluster, isEndOfMessageCluster, message.isOutgoing)
        cornerMask.setTopLeftRadius(cornerRadii[0])
        cornerMask.setTopRightRadius(cornerRadii[1])
        cornerMask.setBottomRightRadius(cornerRadii[2])
        cornerMask.setBottomLeftRadius(cornerRadii[3])
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        cornerMask.mask(canvas)
    }
    // endregion

    // region Interaction
    fun calculateHit(event: MotionEvent) {
        val rawXInt = event.rawX.toInt()
        val rawYInt = event.rawY.toInt()
        val hitRect = Rect(rawXInt, rawYInt, rawXInt, rawYInt)
        val previewRect = Rect()
        binding.mainLinkPreviewParent.getGlobalVisibleRect(previewRect)
        if (previewRect.contains(hitRect)) {
            openURL()
            return
        }
    }

    fun openURL() {
        val url = this.url ?: return
        val activity = context as AppCompatActivity
        ModalUrlBottomSheet(url).show(activity.supportFragmentManager, "Open URL Dialog")
    }
    // endregion
}