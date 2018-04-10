package com.github.rotty3000.osgi.logback;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Bundle;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.admin.LoggerAdmin;
import org.slf4j.ILoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.CallerData;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;

public class LogbackLogListener implements LogListener, LoggerContextListener {

	volatile LoggerContext loggerContext;
	volatile Logger rootLogger;
	volatile LoggerContextVO loggerContextVO;
	final Map<String, LogLevel> initialLogLevels;
	final org.osgi.service.log.admin.LoggerContext osgiLoggerContext;

	public LogbackLogListener(LoggerAdmin loggerAdmin) {
		osgiLoggerContext = loggerAdmin.getLoggerContext(null);
		initialLogLevels = osgiLoggerContext.getLogLevels();

		ILoggerFactory loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();

		if (!(loggerFactory instanceof LoggerContext)) {
			throw new IllegalStateException("This bundle only works with logback-classic");
		}

		loggerContext = (LoggerContext)loggerFactory;
		rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
		loggerContextVO = loggerContext.getLoggerContextRemoteView();

		Map<String, LogLevel> updatedLevels = updateLevels(loggerContext, initialLogLevels);

		osgiLoggerContext.setLogLevels(updatedLevels);

		loggerContext.addListener(this);
	}

	@Override
	public boolean isResetResistant() {
		return true;
	}

	@Override
	public void logged(final LogEntry entry) {
		String loggerName = entry.getLoggerName();
		String message = entry.getMessage();
		Object[] arguments = null;
		Level level = from(entry.getLogLevel());
		AtomicBoolean avoidCallerData = new AtomicBoolean();

		if ("Events.Bundle".equals(loggerName) ||
			"Events.Framework".equals(loggerName) ||
			"LogService".equals(loggerName)) {

			loggerName = formatBundle(entry.getBundle(), loggerName);
			avoidCallerData.set(true);
		}
		else if ("Events.Service".equals(loggerName)) {
			loggerName = formatBundle(entry.getBundle(), loggerName);
			message = message + " {}";
			arguments = new Object[] {entry.getServiceReference()};
			avoidCallerData.set(true);
		}

		Logger logger = loggerContext.getLogger(loggerName);

		// Check to see if there's a logger defined in our configuration and
		// if there is, then make sure it's handled as an override for the
		// effective level.
		if (!logger.equals(rootLogger) && !logger.isEnabledFor(level)) {
			return;
		}

		LoggingEvent le = new LoggingEvent() {

			@Override
			public StackTraceElement[] getCallerData() {
				if (avoidCallerData.get() || callerData != null)
					return callerData;
				return callerData = getCallerData0(entry.getLocation());
			}

			private volatile StackTraceElement[] callerData;

		};

		le.setArgumentArray(arguments);
		le.setMessage(message);
		le.setLevel(level);
		le.setLoggerContextRemoteView(loggerContextVO);
		le.setLoggerName(loggerName);
		le.setThreadName(entry.getThreadInfo());
		le.setThrowableProxy(getThrowableProxy(entry.getException()));
		le.setTimeStamp(entry.getTime());

		rootLogger.callAppenders(le);
	}

	@Override
	public void onLevelChange(Logger logger, Level level) {
		Map<String, LogLevel> updatedLevels = osgiLoggerContext.getLogLevels();

		if (Level.OFF.equals(level)) {
			updatedLevels.remove(logger.getName());
		}
		else {
			updatedLevels.put(logger.getName(), from(level));
		}

		osgiLoggerContext.setLogLevels(updatedLevels);
	}

	@Override
	public void onStart(LoggerContext context) {
		loggerContext = context;
		rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
		loggerContextVO = loggerContext.getLoggerContextRemoteView();

		Map<String, LogLevel> updatedLevels = updateLevels(loggerContext, initialLogLevels);

		osgiLoggerContext.setLogLevels(updatedLevels);
	}

	@Override
	public void onStop(LoggerContext context) {
		osgiLoggerContext.setLogLevels(initialLogLevels);
	}

	@Override
	public void onReset(LoggerContext context) {
		loggerContext = context;
		rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
		loggerContextVO = loggerContext.getLoggerContextRemoteView();

		Map<String, LogLevel> updatedLevels = updateLevels(loggerContext, initialLogLevels);

		osgiLoggerContext.setLogLevels(updatedLevels);
	}

	String formatBundle(Bundle bundle, String loggerName) {
		return new StringBuilder().append(
			loggerName
		).append(
			"."
		).append(
			bundle.getSymbolicName()
		).toString();
	}

	LogLevel from(Level level) {
		if (Level.ALL.equals(level)) {
			return LogLevel.TRACE;
		}
		else if (Level.DEBUG.equals(level)) {
			return LogLevel.DEBUG;
		}
		else if (Level.ERROR.equals(level)) {
			return LogLevel.ERROR;
		}
		else if (Level.INFO.equals(level)) {
			return LogLevel.INFO;
		}
		else if (Level.TRACE.equals(level)) {
			return LogLevel.TRACE;
		}
		else if (Level.WARN.equals(level)) {
			return LogLevel.WARN;
		}

		return LogLevel.WARN;
	}

	Level from(LogLevel logLevel) {
		switch (logLevel) {
			case AUDIT:
				return Level.TRACE;
			case DEBUG:
				return Level.DEBUG;
			case ERROR:
				return Level.ERROR;
			case INFO:
				return Level.INFO;
			case TRACE:
				return Level.TRACE;
			case WARN:
			default:
				return Level.WARN;
		}
	}

	/*
	 * TODO This method should be tuned with the correct packages and
	 * class names.
	 */
	StackTraceElement[] getCallerData0(StackTraceElement stackTraceElement) {
		StackTraceElement[] callerData = CallerData.extract(
			new Throwable(),
			org.osgi.service.log.Logger.class.getName(),
			loggerContext.getMaxCallerDataDepth(),
			loggerContext.getFrameworkPackages());

		if (stackTraceElement != null) {
			if (callerData.length == 0) {
				callerData = new StackTraceElement[] {stackTraceElement};
			}
			else {
				StackTraceElement[] copy = new StackTraceElement[callerData.length + 1];
				copy[0] = stackTraceElement;
				System.arraycopy(callerData, 0, copy, 1, callerData.length);
				callerData = copy;
			}
		}

		return callerData;
	}

	ThrowableProxy getThrowableProxy(Throwable t) {
		if (t == null)
			return null;
		ThrowableProxy throwableProxy = new ThrowableProxy(t);
		if (loggerContext.isPackagingDataEnabled()) {
			throwableProxy.calculatePackagingData();
		}
		return throwableProxy;
	}

	Map<String, LogLevel> updateLevels(LoggerContext loggerContext, Map<String, LogLevel> levels) {
		Map<String, LogLevel> copy = new HashMap<String, LogLevel>(levels);

		loggerContext.getLoggerList().forEach(
			l -> {
				String name = l.getName();

				copy.compute(
					name, (k, v) -> {
						if (l.getLevel() == Level.OFF) {
							return null;
						}

						return from(l.getLevel());
					}
				);
			}
		);

		Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
		levels.put(org.osgi.service.log.Logger.ROOT_LOGGER_NAME, from(root.getLevel()));

		return copy;
	}

}
