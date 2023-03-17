package com.thoughtcrimes.securesms.wallet.scanner

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.zxing.BarcodeFormat
import com.google.zxing.Result
import com.thoughtcrimes.securesms.data.BarcodeData
import com.thoughtcrimes.securesms.home.HomeActivity
import com.thoughtcrimes.securesms.wallet.OnBackPressedListener
import com.thoughtcrimes.securesms.wallet.OnUriScannedListener
import com.thoughtcrimes.securesms.wallet.send.SendFragment
import com.thoughtcrimes.securesms.wallet.widget.Toolbar
import io.beldex.bchat.R
import io.beldex.bchat.databinding.FragmentWalletScannerBinding
import me.dm7.barcodescanner.zxing.ZXingScannerView
import timber.log.Timber
import java.lang.ClassCastException


class WalletScannerFragment(
) : Fragment(), ZXingScannerView.ResultHandler,OnUriScannedListener, OnBackPressedListener
{
    private var onScannedListener: OnScannedListener? = null
  /*  private var activityCallback: SendFragment.Listener? = null*/
    private var sendFragment: SendFragment? = null
    private var uri: String? = null
    var activityCallback: Listener? = null

    fun newInstance(listener: Listener): WalletScannerFragment {
        val instance: WalletScannerFragment = WalletScannerFragment()
        instance.setSendListener(listener)
        return instance
    }
    private fun setSendListener(listener: Listener) {
        this.activityCallback = listener
    }

    interface Listener {
        fun onSendRequest(view: View?)
        fun setBarcodeData(data: BarcodeData?)
        fun walletOnBackPressed() //-
    }

    interface OnScannedListener {
        fun onScanned(qrCode: String?): Boolean
        fun setOnBarcodeScannedListener(onUriScannedListener: OnUriScannedListener?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true);

    }

    lateinit var binding:FragmentWalletScannerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentWalletScannerBinding.inflate(inflater,container,false)
        (activity as HomeActivity).setSupportActionBar(binding.toolbar)
        setHasOptionsMenu(true)
        Timber.d("onCreateView")

        binding.exitButton.setOnClickListener {
            activityCallback?.walletOnBackPressed()
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        Log.d("Beldex","qr code scan onResume()")
        binding.mScannerView.setResultHandler(this)
        binding.mScannerView.startCamera()
        //activityCallback!!.setTitle(getString(R.string.activity_scan_page_title))
        //activityCallback!!.setToolbarButton(Toolbar.BUTTON_BACK)
    }

    override fun handleResult(rawResult: Result) {
        if (rawResult.barcodeFormat == BarcodeFormat.QR_CODE) {
            if (onScannedListener!!.onScanned(rawResult.text)) {
                Log.d("Beldex","value of barcode ${rawResult.text}")
                return
            } else {
                Toast.makeText(
                    activity,
                    getString(R.string.send_qr_address_invalid),
                    Toast.LENGTH_SHORT
                ).show()
                val handler = Handler()
                handler.postDelayed(
                    { binding.mScannerView.resumeCameraPreview(this) },
                    1000
                )
            }
        } else {
            Toast.makeText(activity, getString(R.string.send_qr_invalid), Toast.LENGTH_SHORT).show()
        }

        // Note from dm77:
        // * Wait 2 seconds to resume the preview.
        // * On older devices continuously stopping and resuming camera preview can result in freezing the app.
        // * I don't know why this is the case but I don't have the time to figure out.
        val handler = Handler()
        handler.postDelayed(
            { binding.mScannerView.resumeCameraPreview(this) },
            1000
        )
    }

    override fun onPause() {
        super.onPause()
        Timber.d("onPause")
       /* mScannerView!!.stopCamera()*/
    }

    override fun onStop() {
        super.onStop()
        binding.mScannerView.stopCamera();
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnScannedListener) {
            onScannedListener = context
            onScannedListener!!.setOnBarcodeScannedListener(this)
            activityCallback = context as Listener

        } else {
            throw ClassCastException(
                context.toString()
                        + " must implement Listener"
            )
        }
    }

    override fun onUriScanned(barcodeData: BarcodeData?): Boolean {
        Log.d("Beldex", "value of barcode 7")
        processScannedData(barcodeData, sendFragment = SendFragment())
        Log.d("Beldex", "value of barcode 8")
        return true
    }

    private fun processScannedData(barcodeData: BarcodeData?, sendFragment: SendFragment) {
        Log.d("Beldex", "value of barcode 9 $activityCallback")
        activityCallback!!.onSendRequest(view)
        activityCallback!!.setBarcodeData(barcodeData)
        sendFragment.processScannedData(barcodeData)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.clear()
    }

    override fun onBackPressed(): Boolean {
        return false
    }
}
