package com.thoughtcrimes.securesms.wallet.send

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.thoughtcrimes.securesms.data.*
import com.thoughtcrimes.securesms.model.PendingTransaction
import com.thoughtcrimes.securesms.wallet.send.interfaces.SendConfirm
import io.beldex.bchat.R
import com.thoughtcrimes.securesms.wallet.addressbook.AddressBookActivity

import android.content.Intent
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.*
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.beldex.libbchat.utilities.TextSecurePreferences
import com.thoughtcrimes.securesms.conversation.v2.TransactionLoadingBar
import com.thoughtcrimes.securesms.dependencies.DatabaseComponent
import com.thoughtcrimes.securesms.home.HomeActivity
import com.thoughtcrimes.securesms.model.AsyncTaskCoroutine
import com.thoughtcrimes.securesms.model.Wallet
import com.thoughtcrimes.securesms.model.WalletManager
import com.thoughtcrimes.securesms.util.BChatThreadPoolExecutor
import com.thoughtcrimes.securesms.util.Helper
import com.thoughtcrimes.securesms.wallet.*
import com.thoughtcrimes.securesms.wallet.utils.OpenAliasHelper
import com.thoughtcrimes.securesms.wallet.utils.helper.ServiceHelper
import com.thoughtcrimes.securesms.wallet.utils.pincodeview.CustomPinActivity
import com.thoughtcrimes.securesms.wallet.utils.pincodeview.managers.AppLock
import com.thoughtcrimes.securesms.wallet.utils.pincodeview.managers.LockManager
import io.beldex.bchat.databinding.FragmentSendBinding
import java.lang.ClassCastException
import java.lang.NumberFormatException
import java.util.*
import timber.log.Timber
import java.lang.Exception
import java.math.BigDecimal
import java.util.concurrent.Executor


class SendFragment : Fragment(), OnUriScannedListener,SendConfirm,OnUriWalletScannedListener, OnBackPressedListener {

    val MIXIN = 0

    private var activityCallback: Listener? = null
    lateinit var binding: FragmentSendBinding

    private var isResume:Boolean = false

    private val possibleCryptos: MutableSet<Crypto> = HashSet()
    private var selectedCrypto: Crypto? = null
    val INTEGRATED_ADDRESS_LENGTH = 106
    private var resolvingOA = false
    private var totalFunds: Long = 0
    private var calledUnlockedBalance: Boolean = false


    fun newInstance(listener: Listener): SendFragment? {
        val instance: SendFragment = SendFragment()
        instance.setSendListener(listener)
        return instance
    }

    private fun setSendListener(listener: Listener) {
        this.activityCallback = listener
    }

    interface Listener {
        val prefs: SharedPreferences?
        val totalFunds: Long
        val isStreetMode: Boolean

        fun onPrepareSend(tag: String?, data: TxData?)
        val walletName: String?

        fun onSend(notes: UserNotes?)
        fun onDisposeRequest()
        fun onFragmentDone()
        fun setOnUriScannedListener(onUriScannedListener: OnUriScannedListener?)
        fun setOnUriWalletScannedListener(onUriWalletScannedListener: OnUriWalletScannedListener?)
        fun setBarcodeData(data: BarcodeData?)

        fun getBarcodeData(): BarcodeData?

        fun popBarcodeData(): BarcodeData?

        fun setMode(mode: HomeActivity.Mode?)

        fun getTxData(): TxData?

        fun onBackPressedFun()

        fun walletOnBackPressed() //-
    }
    var onScanListener: OnScanListener? = null
    interface OnScanListener {
        fun onScan()
    }

    fun onCreateTransactionFailed(errorText: String?) {
        createTransactionFailed(errorText)
    }

   private fun openSomeActivityForResult() {
       TextSecurePreferences.setSendAddressDisable(requireContext(),false)
       val intent = Intent(context, AddressBookActivity::class.java)
       resultLauncher.launch(intent)
   }

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val add = data?.getStringExtra("address_value")
            activityCallback?.setBarcodeData(null)
            if(add != null)
            {
                binding.beldexAddressEditTxtLayout.editText!!.setText(add.toString())
            }
        }
    }

    var pendingTx: PendingTx? = null

    // callbacks from send service
    fun onTransactionCreated(txTag: String?, pendingTransaction: PendingTransaction?) {
        pendingTx = PendingTx(pendingTransaction)
        transactionCreated(txTag, pendingTransaction)
    }

    fun disposeTransaction() {
        pendingTx = null
        activityCallback!!.onDisposeRequest()
    }

    //If Transaction successfully completed after call this function
    fun onTransactionSent(txId: String?) {
        hideProgress()
        SendSuccessDialog(this).show(requireActivity().supportFragmentManager,"")
    }

    var committedTx: PendingTx? = null

    @SuppressLint("StringFormatMatches")
    fun onSendTransactionFailed(error: String?) {
        Timber.d("error=%s", error)
        committedTx = null
        sendFailed(getString(R.string.status_transaction_failed, error))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
    companion object {
        @JvmStatic
        fun newInstance(uri: String?): SendFragment {
            val f = SendFragment()
            val args = Bundle()
            args.putString(HomeActivity.REQUEST_URI, uri)
            f.arguments = args
            return f
        }
    }

    private val resultLaunchers = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onResumeFragment()
        }
    }

    var price =0.00

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSendBinding.inflate(inflater, container, false)
        (activity as HomeActivity).setSupportActionBar(binding.toolbar)
        calledUnlockedBalance = true
        binding.currencyTextView.text = TextSecurePreferences.getCurrency(requireActivity()).toString()
        price = if(TextSecurePreferences.getCurrencyAmount(requireActivity())!=null){
            TextSecurePreferences.getCurrencyAmount(requireActivity())!!.toDouble()
        }else{ 0.00}
        binding.scanQrCode.setOnClickListener {
            if(!CheckOnline.isOnline(requireActivity())) {
                Toast.makeText(requireActivity(), R.string.please_check_your_internet_connection, Toast.LENGTH_SHORT).show()
            }else{
                onScanListener?.onScan()
            }
        }
        binding.addressBook.setOnClickListener {
            openSomeActivityForResult()
        }

        //Validate beldex address
        binding.beldexAddressEditTxtLayout.editText?.setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        binding.beldexAddressEditTxtLayout.editText?.setOnEditorActionListener(OnEditorActionListener { v, actionId, event -> // ignore ENTER
                event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
            })
        binding.beldexAddressEditTxtLayout.editText?.onFocusChangeListener =
            OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                if (!hasFocus) {
                    val enteredAddress: String = binding.beldexAddressEditTxtLayout.editText?.text.toString().trim()
                    val dnsOA = dnsFromOpenAlias(enteredAddress)
                    Timber.d("OpenAlias is %s", dnsOA)
                    if (dnsOA != null) {
                        processOpenAlias(dnsOA)
                    }
                }
            }
        binding.beldexAddressEditTxtLayout.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                binding.beldexAddressLayout.setBackgroundResource(R.drawable.bchat_id_text_view_background)
                binding.beldexAddressErrorMessage.visibility = View.GONE
                binding.beldexAddressErrorMessage.text=""
                possibleCryptos.clear()
                selectedCrypto = null
                val address: String = binding.beldexAddressEditTxtLayout.editText?.text.toString().trim()
                if (isIntegratedAddress(address)) {
                    possibleCryptos.add(Crypto.BDX)
                    selectedCrypto = Crypto.BDX
                    binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
                    binding.beldexAddressErrorMessage.visibility = View.VISIBLE
                    binding.beldexAddressErrorMessage.text=getString(R.string.info_paymentid_integrated)
                    setMode(Mode.BDX)
                } else if (isStandardAddress(address)) {
                    possibleCryptos.add(Crypto.BDX)
                    selectedCrypto = Crypto.BDX
                    setMode(Mode.BDX)
                }
                if (possibleCryptos.isEmpty()) {
                    Timber.d("other")
                    setMode(Mode.BDX)
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })

        binding.beldexAmountEditTxtLayout.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if(binding.beldexAmountEditTxtLayout.editText!!.isFocused) {
                    if (s.isNotEmpty()) {
                        if(validateBELDEXAmount(s.toString())) {
                            binding.beldexAmountConstraintLayout.setBackgroundResource(R.drawable.bchat_id_text_view_background)
                            binding.beldexAmountErrorMessage.visibility = View.GONE
                            binding.beldexAmountErrorMessage.text = ""
                            val bdx = getCleanAmountString(s.toString())
                            val amount: BigDecimal = if (bdx != null) {
                                BigDecimal(bdx.toDouble()).multiply(BigDecimal(price))
                            } else {
                                BigDecimal(0L).multiply(BigDecimal(price))
                            }
                            binding.currencyEditText.text = String.format("%.4f", amount)
                        }else{
                            binding.beldexAmountConstraintLayout.setBackgroundResource(R.drawable.error_view_background)
                            binding.beldexAmountErrorMessage.visibility =View.VISIBLE
                            binding.beldexAmountErrorMessage.text=getString(R.string.beldex_amount_valid_error_message)
                        }
                    } else {
                        binding.currencyEditText.text="0.00"
                    }
                }
            }
        })

        binding.beldexAmountEditTxtLayout.editText?.imeOptions =
            EditorInfo.IME_ACTION_DONE or 16777216 // Always use incognito keyboard
        binding.beldexAmountEditTxtLayout.editText?.setOnEditorActionListener { v, actionID, _ ->
            if (actionID == EditorInfo.IME_ACTION_DONE) {
                val imm =
                    v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                createTransactionIfPossible()
                true
            } else {
                false
            }
        }

        binding.sendButton.setOnClickListener {
            createTransactionIfPossible()
        }
        if(TextSecurePreferences.getFeePriority(requireActivity())==0){
            binding.estimatedFeeTextView.text = getString(R.string.estimated_fee,calculateEstimatedFee(1).toString())
            binding.estimatedFeeDescriptionTextView.text =getString(R.string.estimated_fee_description,"Slow")
        }else{
            binding.estimatedFeeTextView.text = getString(R.string.estimated_fee,calculateEstimatedFee(5).toString())
            binding.estimatedFeeDescriptionTextView.text =getString(R.string.estimated_fee_description,"Flash")
        }

        binding.exitButton.setOnClickListener {
            activityCallback?.walletOnBackPressed()
        }

        return binding.root

    }

    private fun createTransactionIfPossible(){
        if(CheckOnline.isOnline(requireContext())) {
            if (!checkAddressNoError()) {
                shakeAddress()
                val enteredAddress: String =
                    binding.beldexAddressEditTxtLayout.editText?.text.toString().trim()
                val dnsOA = dnsFromOpenAlias(enteredAddress)
                if (dnsOA != null) {
                    Log.d("OpenAlias is %s", dnsOA)
                }
                dnsOA?.let { processOpenAlias(it) }
            } else {
                if (binding.beldexAddressEditTxtLayout.editText?.text!!.isNotEmpty() && binding.beldexAmountEditTxtLayout.editText?.text!!.isNotEmpty() && validateBELDEXAmount(binding.beldexAmountEditTxtLayout.editText!!.text.toString()) && binding.beldexAmountEditTxtLayout.editText!!.text.toString()
                        .toDouble() > 0.00
                ) {
                    val txData: TxData = getTxData()
                    txData.destinationAddress =
                        binding.beldexAddressEditTxtLayout.editText?.text.toString().trim()
                    ServiceHelper.ASSET = null

                    if (getCleanAmountString(binding.beldexAmountEditTxtLayout.editText?.text.toString()).equals(
                            Wallet.getDisplayAmount(totalFunds)
                        )
                    ) {
                        val amount =
                            (totalFunds - 10485760)// 10485760 == 050000000
                        val bdx =
                            getCleanAmountString(binding.beldexAmountEditTxtLayout.editText?.text.toString())
                        if (bdx != null) {
                            txData.amount = amount
                        } else {
                            txData.amount = 0L
                        }
                    } else {
                        val bdx =
                            getCleanAmountString(binding.beldexAmountEditTxtLayout.editText?.text.toString())
                        if (bdx != null) {
                            txData.amount = Wallet.getAmountFromString(bdx)
                        } else {
                            txData.amount = 0L
                        }
                    }
                    txData.userNotes =
                        UserNotes("-")//etNotes.getEditText().getText().toString()
                    if(TextSecurePreferences.getFeePriority(requireActivity())==0){
                        txData.priority = PendingTransaction.Priority.Priority_Slow
                    }else{
                        txData.priority = PendingTransaction.Priority.Priority_Flash
                    }
                    txData.mixin = MIXIN
                    binding.beldexAddressEditTxtLayout.editText?.text?.clear()
                    binding.beldexAmountEditTxtLayout.editText?.text?.clear()
                    binding.currencyEditText.text="0.00"

                    //Important
                    val lockManager: LockManager<CustomPinActivity> =
                        LockManager.getInstance() as LockManager<CustomPinActivity>
                    lockManager.enableAppLock(requireActivity(), CustomPinActivity::class.java)
                    val intent = Intent(requireActivity(), CustomPinActivity::class.java)
                    intent.putExtra(AppLock.EXTRA_TYPE, AppLock.UNLOCK_PIN)
                    intent.putExtra("change_pin", false)
                    intent.putExtra("send_authentication", true)
                    resultLaunchers.launch(intent)
                }else if(binding.beldexAmountEditTxtLayout.editText?.text!!.isEmpty()){
                    binding.beldexAmountConstraintLayout.setBackgroundResource(R.drawable.error_view_background)
                    binding.beldexAmountErrorMessage.visibility =View.VISIBLE
                    binding.beldexAmountErrorMessage.text=getString(R.string.beldex_amount_error_message)
                }else if(binding.beldexAmountEditTxtLayout.editText?.text.toString()=="."){
                    binding.beldexAmountConstraintLayout.setBackgroundResource(R.drawable.error_view_background)
                    binding.beldexAmountErrorMessage.visibility =View.VISIBLE
                    binding.beldexAmountErrorMessage.text=getString(R.string.beldex_amount_valid_error_message)
                }else if(!validateBELDEXAmount(binding.beldexAmountEditTxtLayout.editText!!.text.toString())){
                    if (binding.beldexAmountEditTxtLayout.editText!!.text.toString()
                            .toDouble() <= 0.00
                    ) {
                        binding.beldexAmountConstraintLayout.setBackgroundResource(R.drawable.error_view_background)
                        binding.beldexAmountErrorMessage.visibility =View.VISIBLE
                        binding.beldexAmountErrorMessage.text=getString(R.string.beldex_amount_valid_error_message)
                    }else {
                        binding.beldexAmountConstraintLayout.setBackgroundResource(R.drawable.error_view_background)
                        binding.beldexAmountErrorMessage.visibility = View.VISIBLE
                        binding.beldexAmountErrorMessage.text =
                            getString(R.string.beldex_amount_valid_error_message)
                    }
                }else{
                    binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
                    binding.beldexAddressErrorMessage.visibility = View.VISIBLE
                    binding.beldexAddressErrorMessage.text=getString(R.string.beldex_address_error_message)
                }
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.please_check_your_internet_connection), Toast.LENGTH_SHORT).show()
        }
    }

    inner class AsyncGetUnlockedBalance(val wallet: Listener?) :
        AsyncTaskCoroutine<Executor?, Boolean?>() {
        override fun onPreExecute() {
            super.onPreExecute()

        }

        override fun doInBackground(vararg params: Executor?): Boolean {
            totalFunds = wallet!!.totalFunds
            return true
        }

        override fun onPostExecute(result: Boolean?) {

        }
    }

    private fun validateBELDEXAmount(amount:String):Boolean {
        val maxValue = 150000000.00000
        val value = amount.replace(',', '.')
        val regExp ="^(([0-9]{0,9})?|[.][0-9]{0,5})?|([0-9]{0,9}+([.][0-9]{0,5}))\$"
        var isValid = false

        isValid = if (value.matches(Regex(regExp))) {
            if (value == ".") {
                false
            } else {
                try {
                    val dValue = value.toDouble()
                    (dValue <= maxValue && dValue > 0)
                } catch (e:Exception) {
                    false
                }
            }
        } else {
            false
        }
        return isValid
    }

    private fun calculateEstimatedFee(priority:Int): Double {
        return if(WalletManager.getInstance().wallet!=null) {
            val wallet: Wallet = WalletManager.getInstance().wallet
            wallet.estimateTransactionFee(priority)
        }else{
            0.00
        }
    }

    private val CLEAN_FORMAT = "%." + Helper.BDX_DECIMALS.toString() + "f"

    private fun getCleanAmountString(enteredAmount: String): String? {
        return try {
            val amount = enteredAmount.toDouble()
            if (amount >= 0) {
                String.format(Locale.US, CLEAN_FORMAT, amount)
            } else {
                null
            }
        } catch (ex: NumberFormatException) {
            null
        }
    }

    private fun checkAddressNoError(): Boolean {
        return selectedCrypto != null
    }

    private fun checkAddress(): Boolean {
        val ok = checkAddressNoError()
        if (possibleCryptos.isEmpty()) {
            binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
            binding.beldexAddressErrorMessage.visibility = View.VISIBLE
            binding.beldexAddressErrorMessage.text=getString(R.string.send_address_invalid)
        } else {
            binding.beldexAddressLayout.setBackgroundResource(R.drawable.bchat_id_text_view_background)
            binding.beldexAddressErrorMessage.visibility = View.GONE
            binding.beldexAddressErrorMessage.text=""
        }
        return ok
    }

    private fun isStandardAddress(address: String): Boolean {
        return Wallet.isAddressValid(address)
    }

    private fun isIntegratedAddress(address: String): Boolean {
        return (address.length == INTEGRATED_ADDRESS_LENGTH
                && Wallet.isAddressValid(address))
    }

    private fun shakeAddress() {
            binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
            binding.beldexAddressErrorMessage.visibility = View.VISIBLE
            binding.beldexAddressErrorMessage.text=getString(R.string.send_address_invalid)
    }

    private fun processOpenAlias(dnsOA: String?) {
        if (resolvingOA) return  // already resolving - just wait
        activityCallback!!.popBarcodeData()
        if (dnsOA != null) {
            resolvingOA = true
            binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
            binding.beldexAddressErrorMessage.visibility = View.VISIBLE
            binding.beldexAddressErrorMessage.text=getString(R.string.send_address_resolve_openalias)
            OpenAliasHelper.resolve(dnsOA, object : OpenAliasHelper.OnResolvedListener {
                override fun onResolved(dataMap: Map<Crypto?, BarcodeData?>) {
                    resolvingOA = false
                    var barcodeData = dataMap[Crypto.BDX]
                    if (barcodeData == null) barcodeData = dataMap[Crypto.BTC]
                    if (barcodeData != null) {
                        processScannedData(barcodeData)
                    } else {
                        binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
                        binding.beldexAddressErrorMessage.visibility = View.VISIBLE
                        binding.beldexAddressErrorMessage.text=getString(R.string.send_address_not_openalias)
                    }
                }

                override fun onFailure() {
                    resolvingOA = false
                    binding.beldexAddressLayout.setBackgroundResource(R.drawable.error_view_background)
                    binding.beldexAddressErrorMessage.visibility = View.VISIBLE
                    binding.beldexAddressErrorMessage.text=getString(R.string.send_address_not_openalias)
                }
            })
        }
    }

    var inProgress = false
    //Minimized app
    var onTransactionProgress = false

    private fun hideProgress() {
        val prev: Fragment? =
            requireActivity().supportFragmentManager.findFragmentByTag("transaction_progressbar_tag")
        if (prev != null) {
            val df: DialogFragment = prev as DialogFragment
            try {
                df.dismiss()
            } catch (e: IllegalStateException) {
                return
            }
        }
        inProgress = false
    }

    private fun showProgress() {
        TransactionLoadingBar().show(
            requireActivity().supportFragmentManager,
            "transaction_progressbar_tag"
        )
        inProgress = true
    }

    private fun onResumeFragment(){
        Helper.hideKeyboard(activity)
        isResume = true
        refreshTransactionDetails()
        if (pendingTransaction == null && !inProgress) {
            binding.sendButton.isEnabled=false
            binding.sendButton.isClickable=false
            showProgress()
            prepareSend(txData)
        }
    }

    // creates a pending transaction and calls us back with transactionCreated()
    // or createTransactionFailed()
    private fun prepareSend(txData: TxData?) {
        activityCallback!!.onPrepareSend(null, txData)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        activityCallback = context as Listener
        activityCallback!!.setOnUriScannedListener(this)
        activityCallback!!.setOnUriWalletScannedListener(this)
        onScanListener = if (context is OnScanListener) {
            context
        } else {
            throw ClassCastException(
                context.toString()
                        + " must implement ScanListener"
            )
        }
    }

    override fun onResume() {
        super.onResume()
        processScannedData()
        //Minimized app
        if(onTransactionProgress){
            onTransactionProgress = false
            hideProgress()
            refreshTransactionDetails()
            //Continuously Transaction
            this.pendingTransaction = null
            this.pendingTx = null
        }
        if(calledUnlockedBalance) {
            calledUnlockedBalance = false
            AsyncGetUnlockedBalance(activityCallback).execute<Executor>(BChatThreadPoolExecutor.MONERO_THREAD_POOL_EXECUTOR)
        }
    }

    override fun onPause() {
        //Continuously loading progress bar
        if(inProgress) {
            hideProgress()
        }
        super.onPause()
    }
    // QR Scan Stuff
    fun processScannedData(barcodeData: BarcodeData?) {
        activityCallback?.setBarcodeData(barcodeData)
    }

    private fun processScannedData() {
        var barcodeData: BarcodeData? = activityCallback?.getBarcodeData()
        if (barcodeData != null) {
            if (!Helper.ALLOW_SHIFT && barcodeData.asset !== Crypto.BDX) {
                barcodeData = null
                activityCallback?.setBarcodeData(barcodeData)
            }
            if (barcodeData!!.address != null) {
                activityCallback?.setBarcodeData(null)
                binding.beldexAddressEditTxtLayout.editText?.setText(barcodeData.address)
                binding.beldexAmountEditTxtLayout.editText?.setText(barcodeData.amount)
                possibleCryptos.clear()
                selectedCrypto = null
                if (barcodeData.isAmbiguous) {
                    possibleCryptos.addAll(barcodeData.ambiguousAssets)
                } else {
                    possibleCryptos.add(barcodeData.asset)
                    selectedCrypto = barcodeData.asset
                }
                if (checkAddress()) {
                    if (barcodeData.security === BarcodeData.Security.OA_NO_DNSSEC) binding.beldexAddressEditTxtLayout.error =
                        getString(R.string.send_address_no_dnssec) else if (barcodeData.security === BarcodeData.Security.OA_DNSSEC) binding.beldexAddressEditTxtLayout.error =
                        getString(R.string.send_address_openalias)
                }
            } else {
                binding.beldexAddressEditTxtLayout.editText?.text?.clear()
                binding.beldexAmountEditTxtLayout.editText?.text?.clear()
            }
        } else Timber.d("barcodeData=null")
    }

    private fun getTxData(): TxData {
        return txData
    }

    private var txData = TxData()

    enum class Mode {
        BDX, BTC
    }

    private var mode: Mode = Mode.BDX

    fun setMode(aMode: Mode) {
        if (mode != aMode) {
            mode = aMode
            when (aMode) {
                Mode.BDX -> txData = TxData()
                Mode.BTC -> txData = TxDataBtc()
                else -> throw IllegalArgumentException("Mode " + aMode.toString() + " unknown!")
            }
            Timber.d("New Mode = %s", mode.toString())
        }
    }

    fun getMode(): Mode {
        return mode
    }

    override fun onUriScanned(barcodeData: BarcodeData?): Boolean {
        processScannedData(barcodeData)
        return true
    }
    override fun onUriWalletScanned(barcodeData: BarcodeData?): Boolean {
        processScannedData(barcodeData)
        return true
    }

    override fun sendFailed(errorText: String?) {
        val prev: Fragment? =
            requireActivity().supportFragmentManager.findFragmentByTag("transaction_progressbar_tag")
        if (prev != null) {
            val df: DialogFragment = prev as DialogFragment
            try {
                df.dismiss()
            } catch (e: java.lang.IllegalStateException) {
                return
            }
        }
        sendButtonEnabled()
        showAlert(getString(R.string.send_create_tx_error_title), errorText!!)
    }

    private fun showAlert(title: String, message: String) {
        val builder = AlertDialog.Builder(
            requireActivity(), R.style.backgroundColor
        )
        builder.setCancelable(true).setTitle(title).setMessage(message).create().show()
    }

    override fun createTransactionFailed(errorText: String?) {
        hideProgress()
        sendButtonEnabled()
        showAlert(getString(R.string.send_create_tx_error_title), errorText!!)
    }

    override fun transactionCreated(txTag: String?, pendingTransaction: PendingTransaction?) {
        // ignore txTag - the app flow ensures this is the correct tx
        hideProgress()
        if (isResume) {
            this.pendingTransaction = pendingTransaction
            refreshTransactionDetails()
        } else {
            this.disposeTransaction()
        }
    }

    var pendingTransaction: PendingTransaction? = null

    private fun refreshTransactionDetails() {
        if (pendingTransaction != null) {
            val txData: TxData = getTxData()
            try {
                if(pendingTransaction!!.firstTxId !=null) {
                    SendConfirmDialog(pendingTransaction!!,txData, this).show(requireActivity().supportFragmentManager,"")
                }
            }catch(e: java.lang.IllegalStateException){
                //Minimized app
                onTransactionProgress = true
                return
            }catch(e: IndexOutOfBoundsException){
                //Minimized app
                hideProgress()
                Toast.makeText(requireContext(),getString(R.string.please_try_again_later),Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendButtonEnabled(){
        binding.sendButton.isEnabled=true
        binding.sendButton.isClickable=true
    }

    fun send() {
        commitTransaction()
        //Insert Recipient Address
        if(TextSecurePreferences.getSaveRecipientAddress(requireActivity())) {
            val insertRecipientAddress =
                DatabaseComponent.get(requireActivity()).bchatRecipientAddressDatabase()
            try {
                if(pendingTransaction!!.firstTxId != null)
                insertRecipientAddress.insertRecipientAddress(
                    pendingTransaction!!.firstTxId,
                    txData.destinationAddress
                )
            }catch(e: IndexOutOfBoundsException){
                e.message?.let { Log.d("SendFragment->", it) }
            }
        }
        showProgress()
    }

    fun transactionFinished(){
        sendButtonEnabled()
        activityCallback!!.onBackPressedFun()
    }

    private fun commitTransaction() {
        activityCallback!!.onSend(txData.userNotes)
        committedTx = pendingTx
    }

    private fun dnsFromOpenAlias(openalias: String): String? {
        var openalias = openalias
        if (Patterns.DOMAIN_NAME.matcher(openalias).matches()) return openalias
        if (Patterns.EMAIL_ADDRESS.matcher(openalias).matches()) {
            openalias = openalias.replaceFirst("@".toRegex(), ".")
            if (Patterns.DOMAIN_NAME.matcher(openalias).matches()) return openalias
        }
        return null // not an openalias
    }

    override fun onBackPressed(): Boolean {
        return false
    }
}
//endregion