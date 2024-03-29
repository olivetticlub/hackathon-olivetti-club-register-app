package com.olivetti.club

import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.olivetti.club.utils.ConfigurationManager
import it.jolmi.elaconnector.messages.Barcode
import it.jolmi.elaconnector.messages.ElaResponse
import it.jolmi.elaconnector.messages.enums.CodeType
import it.jolmi.elaconnector.messages.enums.StationType
import it.jolmi.elaconnector.messages.enums.Status
import it.jolmi.elaconnector.service.BroadcastValues.SOCKET_ACTION
import it.jolmi.elaconnector.service.BroadcastValues.SOCKET_STATUS
import it.jolmi.elaconnector.service.IElaResponseListener
import it.jolmi.elaconnector.service.printer.ElaPrinterLocalBinder
import it.jolmi.elaconnector.service.printer.IElaPrinter
import it.jolmi.elaconnector.work.ElaService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.lang.ref.WeakReference
import kotlin.random.Random


@ExperimentalCoroutinesApi
class PrinterService(private val context: WeakReference<Context>) : BroadcastReceiver(),
    ServiceConnection,
    IElaResponseListener {

    private val TAG = PrinterService::class.java.simpleName
    private var mSocketConnected = false
    private var mElaConnectorServiceIntent: Intent? = null
    private var elaConnectorService: IElaPrinter? = null
    private var invokeElaConnectorServiceCallback: (IElaPrinter) -> Unit = {}

    private var deal: Deal? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            if (it.action?.equals(SOCKET_ACTION) == true) {
                mSocketConnected = it.getBooleanExtra(SOCKET_STATUS, false)
                val socketConnected: String = if (mSocketConnected) {
                    "CONNECTED"
                } else {
                    "DISCONNECTED"
                }

                Log.d(TAG, socketConnected)
                if (mSocketConnected) {
                    Log.d(TAG, "printer connected, I'm printing deal")
                    print()
                } else {
                    Log.d(TAG, "printer not connected,  I can't print deal")
                    deinit()
                }
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        Log.d(TAG, "onServiceConnected")
        elaConnectorService = (service as ElaPrinterLocalBinder).getService()
        invokeElaConnectorServiceCallback(elaConnectorService!!)
        invokeElaConnectorService { it.attachListener(this) }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "onServiceDisconnected $name")
        elaConnectorService = null
    }

    override fun onElaResponse(elaResponse: ElaResponse) {
        Log.d(TAG, "elaResponse --> $elaResponse")
        elaResponse.status?.let {
            if (it == Status.KO) {
                //Only show popup when KO occurs
                Log.d(TAG, elaResponse.toString())

            }
        }
    }

    override fun onEmptyQueue() {
        Log.d(TAG, "onEmptyQueue")
    }

    init {
        context.get()?.let {
            LocalBroadcastManager.getInstance(it)
                .registerReceiver(this, IntentFilter(SOCKET_ACTION))

            mElaConnectorServiceIntent = Intent(it, ElaService::class.java)
            it.startService(mElaConnectorServiceIntent)
            it.bindService(
                mElaConnectorServiceIntent,
                this,
                Context.BIND_AUTO_CREATE
            )
        }

    }


    fun connectToPrinter() {
        Log.d(TAG, "connecting to printer")
        invokeElaConnectorService { service: IElaPrinter ->
            service.connect("192.168.68.209", 9100)
        }
    }

    fun disconnectToPrinter() {
        Log.d(TAG, "disconnecting to printer")
        elaDisconnect()
    }

    private fun print() {
        this.deal?.let {
            invokeElaConnectorService {
                try {
                    //Print Barcode
                    val headerList = arrayListOf<String>()
                    headerList.add("")
                    headerList.add("")
                    headerList.add("You got a coupon:")
                    headerList.add("${deal?.description}")
                    headerList.add("")

                    val footerList = arrayListOf<String>()
                    footerList.add("")
                    footerList.add("use this coupon at")
                    footerList.add("${deal?.merchant_address}")
                    footerList.add("")
                    footerList.add("")

                    val barcodeString =
                        ConfigurationManager.getInstance(context.get()!!)
                            .storeId /*3 Chars for StoreID*/
                            .plus(/*Special Char*/"-")
                            .plus(Random.nextInt())

                    val barcode = Barcode(
                        headerList,
                        footerList,
                        CodeType.COD_39,
                        barcodeString,
                        StationType.RICEVUTA
                    )

                    it.printCoupon(barcode)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    elaDisconnect()
                }
            }
        }
    }

    fun printCoupon(deal: Deal) {
        this.deal = deal
        connectToPrinter()
    }

    private fun elaDisconnect() {
        invokeElaConnectorService {
            it.disconnect()
        }
    }

    private fun invokeElaConnectorService(invoke: (IElaPrinter) -> Unit) {
        if (elaConnectorService != null) {
            invoke(elaConnectorService!!)
        } else {
            invokeElaConnectorServiceCallback = invoke
            context.get()!!.bindService(
                mElaConnectorServiceIntent,
                this,
                Context.BIND_AUTO_CREATE
            )
        }
    }


    fun deinit() {
        try {
            context.get()?.let {
                it.stopService(mElaConnectorServiceIntent)
                it.unbindService(this)
                LocalBroadcastManager.getInstance(it).unregisterReceiver(this)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }


}
