package com.github.rotty3000.osgi.logback;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

	private volatile LogbackLogListener logbackLogListener;

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		ServiceReference<LoggerAdmin> laSR =
			bundleContext.getServiceReference(LoggerAdmin.class);

		if (laSR == null) {
			return;
		}

		ServiceReference<LogReaderService> lrsSR =
			bundleContext.getServiceReference(LogReaderService.class);

		if (lrsSR == null) {
			return;
		}

		LoggerAdmin loggerAdmin = bundleContext.getService(laSR);
		LogReaderService logReaderService = bundleContext.getService(lrsSR);

		logbackLogListener = new LogbackLogListener(loggerAdmin);
		logReaderService.addLogListener(logbackLogListener);
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		if (logbackLogListener == null) {
			return;
		}

		ServiceReference<LogReaderService> lrsSR =
			bundleContext.getServiceReference(LogReaderService.class);

		if (lrsSR == null) {
			return;
		}

		LogReaderService logReaderService = bundleContext.getService(lrsSR);

		logReaderService.removeLogListener(logbackLogListener);
	}

}
