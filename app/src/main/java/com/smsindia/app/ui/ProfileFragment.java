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

public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        WebView webView = v.findViewById(R.id.webview_profile);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        // USE "" AS DEFAULT (NOT null)
        SharedPreferences prefs = requireActivity().getSharedPreferences("SMSINDIA_USER", 0);
        String uid = prefs.getString("mobile", "");

        if (!uid.isEmpty()) {
            String profileUrl = "https://profile-phi-roan.vercel.app/?uid=" + uid;
            webView.loadUrl(profileUrl);
        } else {
            webView.loadData(
                "<h3 style='text-align:center;color:red;'>Session expired. Please log in again.</h3>",
                "text/html", "UTF-8"
            );
        }

        return v;
    }
}