package com.thoughtcrimes.securesms.wallet.addressbook

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import com.beldex.libbchat.messaging.contacts.Contact
import com.beldex.libbchat.utilities.recipients.Recipient
import com.thoughtcrimes.securesms.conversation.v2.utilities.MentionManagerUtilities
import com.thoughtcrimes.securesms.database.RecipientDatabase
import com.thoughtcrimes.securesms.database.model.ThreadRecord
import com.thoughtcrimes.securesms.dependencies.DatabaseComponent
import com.thoughtcrimes.securesms.mms.GlideRequests
import io.beldex.bchat.databinding.ActivityAddressBookViewBinding

class AddressBookView : LinearLayout {

    private lateinit var binding: ActivityAddressBookViewBinding

    var recipientDatabase: RecipientDatabase? = null

    var thread: ThreadRecord? = null

    // region Lifecycle
    constructor(context: Context) : super(context) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        setUpViewHierarchy()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        setUpViewHierarchy()
    }

    private fun setUpViewHierarchy() {
        binding = ActivityAddressBookViewBinding.inflate(LayoutInflater.from(context), this, true)
    }

    // region Updating
    fun bind(recipient: Recipient, glide: GlideRequests) {

        val threadID =
            DatabaseComponent.get(context).threadDatabase().getOrCreateThreadIdFor(recipient)
        MentionManagerUtilities.populateUserPublicKeyCacheIfNeeded(
            threadID,
            context
        ) // FIXME: This is a bad place to do this
        val address = recipient.address.serialize()


        if(getBeldexAddress(address) != null || getBeldexAddress(address)?.isNotEmpty() == true)
        {
        binding.nameTextView.text =
            if (recipient.isGroupRecipient) recipient.name else getUserDisplayName(address)

        binding.addressTextView.text = getBeldexAddress(address)
        }

    }
     fun getBeldexAddress(publicKey: String): String? {
        val contact = DatabaseComponent.get(context).bchatContactDatabase()
            .getContactWithBchatID(publicKey)
        return contact?.displayBeldexAddress(Contact.ContactContext.REGULAR) ?: publicKey
    }
    private fun getUserDisplayName(publicKey: String): String {
        val contact = DatabaseComponent.get(context).bchatContactDatabase()
            .getContactWithBchatID(publicKey)
        return contact?.displayName(Contact.ContactContext.REGULAR) ?: publicKey
    }


    fun copyAction(): CardView {
        return binding.copyActionCardView
    }
    fun sendAction(): CardView {
        return binding.sendActionCardView
    }
}
// endregion