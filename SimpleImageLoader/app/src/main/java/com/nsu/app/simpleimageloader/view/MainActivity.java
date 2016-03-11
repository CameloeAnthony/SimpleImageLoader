package com.nsu.app.simpleimageloader.view;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;

import com.nsu.app.simpleimageloader.R;
import com.nsu.app.simpleimageloader.view.ImagesFragment;

/**
 * Created by Anthony on 2016/3/10.
 * Class Note:
 */
public class MainActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);
        if (fragment == null) {
            fragment = new ImagesFragment();
            fm.beginTransaction().add(R.id.fragment_container, fragment)
                    .commit();
        }

    }


}

