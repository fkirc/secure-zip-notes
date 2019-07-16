package com.ditronic.securezipnotes.util

import android.app.Activity

/*import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;*/

object BannerAds {

    fun loadBottomAdsBanner(ac: Activity) {
        val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        val PRODUCTION_AD_UNIT_ID = "ca-app-pub-3394747202744753/2613883108"

        /*MobileAds.initialize(ac, ac.getResources().getString(R.string.admob_app_id));
        final AdView mAdView = new AdView(ac);
        mAdView.setAdSize(AdSize.BANNER);
        if (BuildConfig.DEBUG) {
            mAdView.setAdUnitId(TEST_AD_UNIT_ID);
        } else {
            mAdView.setAdUnitId(PRODUCTION_AD_UNIT_ID);
        }
        // We need to create the AdView dynamically to set the ad unit id dynamically
        final FrameLayout frameLayout = ac.findViewById(R.id.adView);
        frameLayout.addView(mAdView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);*/
    }
}
