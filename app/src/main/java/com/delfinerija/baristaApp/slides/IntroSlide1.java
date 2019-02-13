package com.delfinerija.baristaApp.slides;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.airbnb.lottie.LottieAnimationView;
import com.delfinerija.baristaApp.R;

import agency.tango.materialintroscreen.SlideFragment;

public class IntroSlide1 extends SlideFragment {

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.intro_slide_number1,container,false);
        LottieAnimationView lottieAnimationView = view.findViewById(R.id.drink_animation);
        lottieAnimationView.playAnimation();
        return view;
    }

    @Override
    public int backgroundColor() {
        return R.color.textLight;
    }

    @Override
    public int buttonsColor() {
        return R.color.textLight;
    }

    @Override
    public boolean canMoveFurther() {
        return true;
    }
}
