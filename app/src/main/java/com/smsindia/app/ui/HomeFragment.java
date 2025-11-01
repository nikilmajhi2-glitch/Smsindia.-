package com.smsindia.app.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.fragment.app.Fragment;
import com.smsindia.app.R;

public class HomeFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_home, container, false);

        WebView webView = v.findViewById(R.id.webview_home);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webView.setWebViewClient(new WebViewClient());

        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        String uid = prefs.getString("mobile", "");

        String url = "https://smsindia-homepage.vercel.app/?uid=" + uid;
        webView.loadUrl(url);

        return v;
    }
}