package com.olivetti.club

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.olivetti.club.repositories.MerchantRepository
import com.olivetti.club.utils.CallbackHandler
import kotlinx.android.synthetic.main.activity_merchant_onboarding.*
import kotlinx.android.synthetic.main.activity_welcome.nextButton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MerchantOnboardingActivity : Activity() {
    private val TAG = MerchantOnboardingActivity::class.java.simpleName
    private val service = OlivettiClubBackendServiceFactory.create()
    lateinit var merchantRepository: MerchantRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_merchant_onboarding)
        merchantRepository = MerchantRepository(this)

        nextButton.setOnClickListener {
            createMerchant(
                shopName.text.toString(),
                vat.text.toString(),
                shopAddresss.text.toString(),
                ateco.text.toString()
            )
            merchantRepository.saveMerchant(shopName.text.toString())
            startActivity(Intent(this, GenerateCouponActivity::class.java))
            finish()
        }
    }

    private fun createMerchant(shopName: String, vat: String, shopAddress: String, ateco: String) {
        val request = MerchantCreationRequest(
            shopName,
            vat,
            ateco,
            shopAddress
        )
        Log.d(TAG, request.toString())

        val callbackHandler = CallbackHandler<MerchantCreationResponse>(applicationContext, TAG)
        service.createMerchant(request).enqueue(callbackHandler)
    }


}
