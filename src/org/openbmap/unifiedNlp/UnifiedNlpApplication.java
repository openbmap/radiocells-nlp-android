package org.openbmap.unifiedNlp;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;

import android.app.Application;

@ReportsCrashes(
		formKey = "", // This is required for backward compatibility but not used
		formUri = "http://radiocells.org/openbmap/uploads/crash_report"
		)	
public class UnifiedNlpApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		ACRA.init(this);
	}

}
