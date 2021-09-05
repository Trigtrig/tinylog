package org.tinylog.impl.backend;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.tinylog.core.Framework;
import org.tinylog.core.Level;
import org.tinylog.core.backend.LevelVisibility;
import org.tinylog.core.backend.LoggingBackend;
import org.tinylog.core.context.ContextStorage;
import org.tinylog.core.format.message.MessageFormatter;
import org.tinylog.core.internal.InternalLogger;
import org.tinylog.core.runtime.StackTraceLocation;
import org.tinylog.impl.LogEntry;
import org.tinylog.impl.LogEntryValue;
import org.tinylog.impl.WritingThread;
import org.tinylog.impl.context.ThreadLocalContextStorage;
import org.tinylog.impl.writers.AsyncWriter;
import org.tinylog.impl.writers.Writer;

/**
 * Native logging backend for tinylog.
 */
public class NativeLoggingBackend implements LoggingBackend {

	private final Framework framework;
	private final ContextStorage contextStorage;
	private final LoggingConfiguration configuration;
	private final WritingThread writingThread;

	/**
	 * @param framework The actual framework instance
	 * @param configuration All configured writers mapped to severity levels and tags
	 * @param writingThread The writing thread for enqueuing log entries for async writers (can be {@code null} if there
	 *                      are no async writers)
	 */
	public NativeLoggingBackend(Framework framework, LoggingConfiguration configuration, WritingThread writingThread) {
		this.framework = framework;
		this.contextStorage = new ThreadLocalContextStorage();
		this.configuration = configuration;
		this.writingThread = writingThread;

		framework.registerHook(new LifeCycleHook(configuration.getAllWriters(), writingThread));
	}

	@Override
	public ContextStorage getContextStorage() {
		return contextStorage;
	}

	@Override
	public LevelVisibility getLevelVisibility(String tag) {
		if (tag == null) {
			tag = LevelConfiguration.UNTAGGED_PLACEHOLDER;
		}

		return new LevelVisibility(
			configuration.getWriters(tag, Level.TRACE).getAllWriters().size() > 0,
			configuration.getWriters(tag, Level.DEBUG).getAllWriters().size() > 0,
			configuration.getWriters(tag, Level.INFO).getAllWriters().size() > 0,
			configuration.getWriters(tag, Level.WARN).getAllWriters().size() > 0,
			configuration.getWriters(tag, Level.ERROR).getAllWriters().size() > 0
		);
	}

	@Override
	public boolean isEnabled(StackTraceLocation location, String tag, Level level) {
		if (tag == null) {
			tag = LevelConfiguration.UNTAGGED_PLACEHOLDER;
		}

		Level effectiveLevel = getLevelConfiguration(location.push()).getLevel(tag);
		return level.isAtLeastAsSevereAs(effectiveLevel);
	}

	@Override
	public void log(StackTraceLocation location, String tag, Level level, Throwable throwable, Object message,
					Object[] arguments, MessageFormatter formatter) {
		String internalTag = tag == null ? LevelConfiguration.UNTAGGED_PLACEHOLDER : tag;
		Level effectiveLevel = getLevelConfiguration(location.push()).getLevel(internalTag);

		if (level.isAtLeastAsSevereAs(effectiveLevel)) {
			WriterRepository repository = configuration.getWriters(internalTag, level);

			LogEntry logEntry = createLogEntry(
				location.push(),
				tag,
				level,
				throwable,
				message,
				arguments,
				formatter,
				repository.getRequiredLogEntryValues()
			);

			for (Writer writer : repository.getSyncWriters()) {
				try {
					writer.log(logEntry);
				} catch (Exception ex) {
					if (!Objects.equals(InternalLogger.TAG, tag)) {
						InternalLogger.error(ex, "Failed to write log entry");
					}
				}
			}

			for (AsyncWriter writer : repository.getAsyncWriters()) {
				writingThread.enqueue(writer, logEntry);
			}
		}
	}

	/**
	 * Gets the assigned level configuration for the passed stack trace location.
	 *
	 * <p>
	 *     The level configuration can depend on the actual package or class name.
	 * </p>
	 *
	 * @param location The stack trace location of the caller
	 * @return The assigned level configuration
	 */
	private LevelConfiguration getLevelConfiguration(StackTraceLocation location) {
		Map<String, LevelConfiguration> severityLevels = configuration.getSeverityLevels();

		if (severityLevels.size() == 1) {
			return severityLevels.get("");
		} else {
			String packageOrClass = location.getCallerClassName();
			while (true) {
				LevelConfiguration levelConfiguration = severityLevels.get(packageOrClass);
				if (levelConfiguration == null) {
					packageOrClass = reducePackageOrClass(packageOrClass);
				} else {
					return levelConfiguration;
				}
			}
		}
	}

	/**
	 * Removes the last segment of a package or class name.
	 *
	 * <p>
	 *     For example, "com.example" will be returned for "com.example.foo" or "com.example.Foo" will be returned for
	 *     "com.example.Foo$Bar".
	 * </p>
	 *
	 * @param packageOrClass The package or class name to reduce
	 * @return The passed package or class name without its last segment
	 */
	private static String reducePackageOrClass(String packageOrClass) {
		int index = packageOrClass.length();

		while (index-- > 0) {
			char character = packageOrClass.charAt(index);
			if (character == '.' || character == '$') {
				return packageOrClass.substring(0, index);
			}
		}

		return "";
	}

	/**
	 * Creates a log entry.
	 *
	 * @param location The stack trace location of the caller
	 * @param tag The assigned tag
	 * @param level The severity level
	 * @param throwable The logged exception or any other kind of throwable
	 * @param message The logged text message
	 * @param arguments The argument values for all placeholders in the text message
	 * @param formatter The message formatter for replacing placeholder with the provided arguments
	 * @param logEntryValues Only log entry values in this set have to be filled with real data
	 * @return The created log entry
	 */
	private LogEntry createLogEntry(StackTraceLocation location, String tag, Level level, Throwable throwable,
									Object message, Object[] arguments, MessageFormatter formatter,
									Set<LogEntryValue> logEntryValues) {
		StackTraceElement stackTraceElement = null;
		String className = null;

		if (logEntryValues.contains(LogEntryValue.METHOD)
			|| logEntryValues.contains(LogEntryValue.FILE)
			|| logEntryValues.contains(LogEntryValue.LINE)) {
			stackTraceElement = location.getCallerStackTraceElement();
		} else if (logEntryValues.contains(LogEntryValue.CLASS)) {
			className = location.getCallerClassName();
		}

		return createLogEntry(
			stackTraceElement, className, tag, level, throwable, message, arguments, formatter, logEntryValues
		);
	}

	/**
	 * Creates a log entry.
	 *
	 * @param stackTraceElement The stack trace element of the caller
	 * @param className The class name of the caller
	 * @param tag The assigned tag
	 * @param level The severity level
	 * @param throwable The logged exception or any other kind of throwable
	 * @param message The logged text message
	 * @param arguments The argument values for all placeholders in the text message
	 * @param formatter The message formatter for replacing placeholder with the provided arguments
	 * @param logEntryValues Only log entry values in this set have to be filled with real data
	 * @return The created log entry
	 */
	private LogEntry createLogEntry(StackTraceElement stackTraceElement, String className, String tag, Level level,
									Throwable throwable, Object message, Object[] arguments, MessageFormatter formatter,
									Set<LogEntryValue> logEntryValues) {
		return new LogEntry(
			logEntryValues.contains(LogEntryValue.TIMESTAMP) ? Instant.now() : null,
			logEntryValues.contains(LogEntryValue.UPTIME) ? framework.getRuntime().getUptime() : null,
			logEntryValues.contains(LogEntryValue.THREAD) ? Thread.currentThread() : null,
			contextStorage.getMapping(),
			stackTraceElement == null ? className : stackTraceElement.getClassName(),
			stackTraceElement == null ? null : stackTraceElement.getMethodName(),
			stackTraceElement == null ? null : stackTraceElement.getFileName(),
			stackTraceElement == null ? -1 : stackTraceElement.getLineNumber(),
			tag,
			level,
			formatter == null
				? message == null ? null : message.toString()
				: formatter.format((String) message, arguments),
			throwable
		);
	}

}
