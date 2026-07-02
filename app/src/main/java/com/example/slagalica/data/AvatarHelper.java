package com.example.slagalica.data;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.slagalica.R;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AvatarHelper {

    private static final Map<String, Integer> regionRankCache = new HashMap<>();

    public static void setAvatarFrame(ImageView imageView, int regionRank) {
        if (imageView == null) return;
        if (regionRank == 1) {
            imageView.setBackgroundResource(R.drawable.profile_frame_gold);
        } else if (regionRank == 2) {
            imageView.setBackgroundResource(R.drawable.profile_frame_silver);
        } else if (regionRank == 3) {
            imageView.setBackgroundResource(R.drawable.profile_frame_bronze);
        } else {
            imageView.setBackgroundResource(R.drawable.profile_frame);
        }
    }

    public static void loadAvatar(ImageView imageView, String uid, String avatarUrl) {
        if (imageView == null) return;
        loadImage(imageView, avatarUrl);
        loadFrame(imageView, uid);
    }

    private static void loadImage(ImageView imageView, String url) {
        Glide.with(imageView.getContext())
                .load(url != null && !url.isEmpty() ? url : R.drawable.default_profile)
                .transform(new CircleCrop())
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(imageView);
    }

    private static void loadFrame(ImageView imageView, String uid) {
        if (uid == null) return;
        Integer cached = regionRankCache.get(uid);
        if (cached != null) {
            setAvatarFrame(imageView, cached);
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long rank = doc.getLong("lastMonthRegionRank");
                        int r = rank != null ? rank.intValue() : 0;
                        regionRankCache.put(uid, r);
                        setAvatarFrame(imageView, r);
                    }
                });
    }
}
