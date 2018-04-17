package com.github.rotty3000.osgi.logback;

import java.util.AbstractMap.SimpleEntry;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.util.tracker.ServiceTracker;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator {

	private volatile ServiceTracker<LoggerAdmin, LRST> lat;

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		lat = new ServiceTracker<LoggerAdmin, LRST>(
			bundleContext, LoggerAdmin.class, null) {

			@Override
			public LRST addingService(
				ServiceReference<LoggerAdmin> reference) {

				LoggerAdmin loggerAdmin = bundleContext.getService(reference);

				LRST lrst = new LRST(bundleContext, loggerAdmin);

				lrst.open();

				return lrst;
			}

			@Override
			public void removedService(
				ServiceReference<LoggerAdmin> reference, LRST lrst) {

				lrst.close();
			}
		};

		lat.open();
	}

	@Override
	public void stop(BundleContext bundleContext) throws Exception {
		lat.close();
	}

	class LRST extends ServiceTracker<LogReaderService, Pair> {

		public LRST(BundleContext context, LoggerAdmin loggerAdmin) {
			super(context, LogReaderService.class, null);

			this.loggerAdmin = loggerAdmin;
		}

		@Override
		public Pair addingService(
			ServiceReference<LogReaderService> reference) {

			LogReaderService logReaderService = context.getService(reference);

			LogbackLogListener logbackLogListener = new LogbackLogListener(loggerAdmin);

			logReaderService.addLogListener(logbackLogListener);

			return new Pair(logReaderService, logbackLogListener);
		}

		@Override
		public void removedService(
			ServiceReference<LogReaderService> reference,
			Pair pair) {

			pair.getKey().removeLogListener(pair.getValue());
		}

		private final LoggerAdmin loggerAdmin;

	}

	class Pair extends SimpleEntry<LogReaderService, LogbackLogListener> {

		private static final long serialVersionUID = 1L;

		public Pair(LogReaderService logReaderService, LogbackLogListener logbackLogListener) {
			super(logReaderService, logbackLogListener);
		}

	}

}
