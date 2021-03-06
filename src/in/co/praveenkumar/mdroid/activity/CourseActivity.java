package in.co.praveenkumar.mdroid.activity;

import in.co.praveenkumar.R;
import in.co.praveenkumar.mdroid.dialog.PlaygamesDialog;
import in.co.praveenkumar.mdroid.dialog.RateDialog;
import in.co.praveenkumar.mdroid.fragment.CourseFragment;
import in.co.praveenkumar.mdroid.helper.ApplicationClass;
import in.co.praveenkumar.mdroid.helper.Param;
import in.co.praveenkumar.mdroid.playgames.GameUnlocker;
import in.co.praveenkumar.mdroid.view.SlidingTabLayout;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import com.startapp.android.publish.StartAppAd;
import com.startapp.android.publish.StartAppSDK;

public class CourseActivity extends BaseNavigationActivity {
	private ViewPager viewPager;
	private static final String[] TABS = { "MY COURSES", "FAVOURITE COURSES" };
	private StartAppAd startAppAd;
	PlaygamesDialog mPlaygamesDialog;
	RateDialog mRateDialog;
	SharedPreferences mSharedPrefs;
	SharedPreferences.Editor mSharedPrefseditor;
	int dialogCount;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/**
		 * StartAppSDK and StartAppAd Init. SDK init is only required in the
		 * MainActivity - entry activities to the app.
		 */
		StartAppSDK.init(this, Param.STARTAPP_DEV_ID, Param.STARTAPP_APP_ID,
				false);
		startAppAd = new StartAppAd(this);

		setContentView(R.layout.activity_course);
		setUpDrawer();

		// Send a tracker
		((ApplicationClass) getApplication())
				.sendScreen(Param.GA_SCREEN_COURSE);

		getSupportActionBar().setTitle("Moodle Home");
		getSupportActionBar().setIcon(R.drawable.ic_actionbar_icon);

		FragmentPagerAdapter mAdapter = new CourseTabsAdapter(
				getSupportFragmentManager());

		viewPager = (ViewPager) findViewById(R.id.course_pager);
		viewPager.setOffscreenPageLimit(2);
		viewPager.setAdapter(mAdapter);

		SlidingTabLayout mSlidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
		mSlidingTabLayout.setViewPager(viewPager);

		// Dialog related work
		mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPrefseditor = mSharedPrefs.edit();
		dialogCount = mSharedPrefs.getInt("dialogCount", 0);
		mSharedPrefseditor.putInt("dialogCount", dialogCount + 1);
		mSharedPrefseditor.commit();

		if ((dialogCount + 2) % 3 == 1
				&& !mSharedPrefs.getBoolean("isRated", false)) {
			mRateDialog = new RateDialog(this, new DialogActionListener());
			mRateDialog.show();
		}
	}

	class CourseTabsAdapter extends FragmentPagerAdapter {
		public CourseTabsAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			/*
			 * We use bundle to pass course listing type because, by using other
			 * methods we will lose the listing type information in the fragment
			 * on onResume (this calls empty constructor). For the same reason
			 * interface may not work. Bundles are passed again on onResume
			 */
			switch (position) {
			case 0:
				CourseFragment userCourses = new CourseFragment();

				// Set the listing type to only user courses in bundle.
				Bundle bundle = new Bundle();
				bundle.putInt("coursesType", CourseFragment.TYPE_USER_COURSES);
				userCourses.setArguments(bundle);

				return userCourses;
			case 1:
				CourseFragment favCourses = new CourseFragment();

				// Set the listing type to only user courses in bundle.
				Bundle bundle1 = new Bundle();
				bundle1.putInt("coursesType", CourseFragment.TYPE_FAV_COURSES);
				favCourses.setArguments(bundle1);

				return favCourses;
			}
			return null;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return TABS[position];
		}

		@Override
		public int getCount() {
			return TABS.length;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		startAppAd.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
		startAppAd.onPause();
	}

	@Override
	public void onBackPressed() {
		if (!isProUser() && !Param.hideAdsForSession && Param.STARTAPP_EXIT_ADS) {
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(this);
			long now = System.currentTimeMillis();
			long last = settings.getLong("startapp_last_served", now
					- Param.STARTAPP_INTERSTITIAL_MAX_FREQ);

			if (now - last >= Param.STARTAPP_INTERSTITIAL_MAX_FREQ) {

				// Send a tracker event
				((ApplicationClass) getApplication()).sendEvent(
						Param.GA_EVENT_CAT_ADS,
						Param.GA_EVENT_ADS_STARTAPP_EXITAD);

				startAppAd.onBackPressed();
			}
		}
		super.onBackPressed();
	}

	@Override
	public void onSignInSucceeded() {
		// Check for any playGames unlocks
		GameUnlocker mGameUnlocker = new GameUnlocker(getApplicationContext());
		if (isSignedIn())
			mGameUnlocker.publishAcheivements(getApiClient());
		else
			System.out.println("Not logged in! :(");
	}

	@Override
	public void onSignInFailed() {
		if (dialogCount % 3 == 1) {
			mPlaygamesDialog = new PlaygamesDialog(this,
					new DialogActionListener());
			mPlaygamesDialog.show();
		}
	}

	public class DialogActionListener {
		public final static int CANCEL = 1;
		public final static int CONNECT_PLAYGAMES = 2;
		public final static int RATE = 3;

		public void doAction(int action) {
			if (action == CONNECT_PLAYGAMES) {
				mHelper.beginUserInitiatedSignIn();
				if (mPlaygamesDialog != null)
					mPlaygamesDialog.dismiss();
			}

			if (action == CANCEL) {
				if (mPlaygamesDialog != null)
					mPlaygamesDialog.dismiss();
				if (mRateDialog != null)
					mRateDialog.dismiss();
			}

			if (action == RATE) {
				if (mRateDialog != null)
					mRateDialog.dismiss();
				final String appPackageName = getPackageName();
				try {
					startActivity(new Intent(Intent.ACTION_VIEW,
							Uri.parse("market://details?id=" + appPackageName)));
				} catch (android.content.ActivityNotFoundException anfe) {
					startActivity(new Intent(
							Intent.ACTION_VIEW,
							Uri.parse("http://play.google.com/store/apps/details?id="
									+ appPackageName)));
				}
				mSharedPrefseditor.putBoolean("isRated", true);
				mSharedPrefseditor.commit();
			}
		}

	}

}
