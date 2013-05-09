package com.appliedanalog.glass.generic;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.widget.TextView;

public class MainActivity extends Activity {
	TextView label1;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		label1 = (TextView)findViewById(R.id.label1);
	}
	
	boolean goingOut = false;
	
	AnimatorListener aniListener = new AnimatorListener(){
		@Override
		public void onAnimationEnd(Animator animation) {
			int dest = goingOut ? 0 : 50;
			label1.animate().x(dest).y(dest).setDuration(1000).setListener(aniListener);
			goingOut = !goingOut;
		}
		
		@Override
		public void onAnimationCancel(Animator animation) { }

		@Override
		public void onAnimationRepeat(Animator animation) { }

		@Override
		public void onAnimationStart(Animator animation) { }
	};
	
	@Override
	protected void onStart(){
		super.onStart();
		
		goingOut = true;
		label1.animate().x(50).y(50).setDuration(1000).setListener(aniListener);
	}

}
