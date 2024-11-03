/**
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package org.slf4j;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.helpers.SubstituteLogger;
import org.slf4j.helpers.SubstituteLoggerFactory;
import org.slf4j.helpers.Util;
import org.slf4j.impl.StaticLoggerBinder;

/**
 * The <code>LoggerFactory</code> is a utility class producing Loggers for
 * various logging APIs, most notably for log4j, logback and JDK 1.4 logging.
 * Other implementations such as {@link org.slf4j.impl.NOPLogger NOPLogger} and
 * {@link org.slf4j.impl.SimpleLogger SimpleLogger} are also supported.
 * <p/>
 * <p/>
 * <code>LoggerFactory</code> is essentially a wrapper around an
 * {@link ILoggerFactory} instance bound with <code>LoggerFactory</code> at
 * compile time.
 * <p/>
 * <p/>
 * Please note that all methods in <code>LoggerFactory</code> are static.
 * 
 * 
 * @author Alexander Dorokhine
 * @author Robert Elliot
 * @author Ceki G&uuml;lc&uuml;
 * 
 */
public final class LoggerFactory {

    static final String CODES_PREFIX = "http://www.slf4j.org/codes.html";

    static final String NO_STATICLOGGERBINDER_URL = CODES_PREFIX + "#StaticLoggerBinder";
    static final String MULTIPLE_BINDINGS_URL = CODES_PREFIX + "#multiple_bindings";
    static final String NULL_LF_URL = CODES_PREFIX + "#null_LF";
    static final String VERSION_MISMATCH = CODES_PREFIX + "#version_mismatch";
    static final String SUBSTITUTE_LOGGER_URL = CODES_PREFIX + "#substituteLogger";
    static final String LOGGER_NAME_MISMATCH_URL = CODES_PREFIX + "#loggerNameMismatch";
    static final String REPLAY_URL = CODES_PREFIX + "#replay";

    static final String UNSUCCESSFUL_INIT_URL = CODES_PREFIX + "#unsuccessfulInit";
    static final String UNSUCCESSFUL_INIT_MSG = "org.slf4j.LoggerFactory in failed state. Original exception was thrown EARLIER. See also " + UNSUCCESSFUL_INIT_URL;

    // 未初始化
    static final int UNINITIALIZED = 0;
    // 初始化正在进行中
    static final int ONGOING_INITIALIZATION = 1;
    // 初始化失败
    static final int FAILED_INITIALIZATION = 2;
    // 初始化成功
    static final int SUCCESSFUL_INITIALIZATION = 3;
    // 初始化回退到无操作模式
    static final int NOP_FALLBACK_INITIALIZATION = 4;

    // 当前初始化状态
    static volatile int INITIALIZATION_STATE = UNINITIALIZED;
    // 用于在初始化过程中提供临时的日志记录状态
    static final SubstituteLoggerFactory SUBST_FACTORY = new SubstituteLoggerFactory();
    // 用于在初始化失败或回退时提供无操作(NOP)的日志记录功能
    static final NOPLoggerFactory NOP_FALLBACK_FACTORY = new NOPLoggerFactory();

    // Support for detecting mismatched logger names.
    // 一个字符串常量，表示系统属性的键名，用于检测日志名称不匹配
    static final String DETECT_LOGGER_NAME_MISMATCH_PROPERTY = "slf4j.detectLoggerNameMismatch";
    // 一个字符串常量，表示系统属性的键名，用于获取 Java 供应商的 URL。
    static final String JAVA_VENDOR_PROPERTY = "java.vendor.url";
    // 一个布尔变量，用于指示是否启用日志名称不匹配的检测。其值通过调用 Util.safeGetBooleanSystemProperty 方法从系统属性中读取。
    static boolean DETECT_LOGGER_NAME_MISMATCH = Util.safeGetBooleanSystemProperty(DETECT_LOGGER_NAME_MISMATCH_PROPERTY);

    /**
     * It is LoggerFactory's responsibility to track version changes and manage
     * the compatibility list.
     * <p/>
     * <p/>
     * It is assumed that all versions in the 1.6 are mutually compatible.
     */
    // 存储与某个API兼容的Java版本列表。
    static private final String[] API_COMPATIBILITY_LIST = new String[] { "1.6", "1.7" };

    // private constructor prevents instantiation
    private LoggerFactory() {
    }

    /**
     * Force LoggerFactory to consider itself uninitialized.
     * <p/>
     * <p/>
     * This method is intended to be called by classes (in the same package) for
     * testing purposes. This method is internal. It can be modified, renamed or
     * removed at any time without notice.
     * <p/>
     * <p/>
     * You are strongly discouraged from calling this method in production code.
     */

    // 强制 LoggerFactory 认为自己未初始化。
    static void reset() {
        INITIALIZATION_STATE = UNINITIALIZED;
    }

    /**
     * 初始化
     * 1、调用bind方法进行绑定
     * 2、执行版本检查
     */
    private final static void performInitialization() {
        bind();
        if (INITIALIZATION_STATE == SUCCESSFUL_INITIALIZATION) {
            versionSanityCheck();
        }
    }

    // 用于检查给定的字符串 msg 是否包含特定的子字符串
    // "org/slf4j/impl/StaticLoggerBinder" 或
    // "org.slf4j.impl.StaticLoggerBinder"。
    private static boolean messageContainsOrgSlf4jImplStaticLoggerBinder(String msg) {
        if (msg == null)
            return false;
        if (msg.contains("org/slf4j/impl/StaticLoggerBinder"))
            return true;
        if (msg.contains("org.slf4j.impl.StaticLoggerBinder"))
            return true;
        return false;
    }

    /**
     * 这段代码的主要功能是初始化SLF4J日志框架。具体步骤如下：
     * 1、检查是否为Android环境：
     *      如果是Android环境，跳过后续检查。
     * 2、查找并报告多重绑定：
     *      查找所有可能的 StaticLoggerBinder 类的路径，并报告多重绑定问题。
     * 3、绑定StaticLoggerBinder：
     *      尝试获取 StaticLoggerBinder 的单例实例，如果成功，设置初始化状态为成功。
     * 4、处理异常：
     *      NoClassDefFoundError：
     *              如果找不到 StaticLoggerBinder 类，
     *              设置初始化状态为无操作（NOP）模式，并报告相关错误信息。
     *      NoSuchMethodError：
     *              如果方法 getSingleton 不存在，
     *              设置初始化状态为失败，并报告版本不兼容问题。
     *      其他异常：
     *              设置初始化状态为失败，并报告意外的初始化失败。
     * 5、清理工作：无论成功还是失败，执行清理工作。
     */
    private final static void bind() {
        try {
            Set<URL> staticLoggerBinderPathSet = null;
            // skip check under android, see also
            // http://jira.qos.ch/browse/SLF4J-328
            if (!isAndroid()) {
                // 查找并返回所有可能的 StaticLoggerBinder 类的路径。
                staticLoggerBinderPathSet = findPossibleStaticLoggerBinderPathSet();
                // 检查类路径中是否存在多个SLF4J绑定，并在存在多个绑定时打印警告信息。
                reportMultipleBindingAmbiguity(staticLoggerBinderPathSet);
            }
            // 尝试获取 StaticLoggerBinder 的单例实例，如果成功，设置初始化状态为成功。
            // the next line does the binding
            StaticLoggerBinder.getSingleton();
            INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION;
            // 这段代码的功能是报告实际绑定的日志工厂类型
            reportActualBinding(staticLoggerBinderPathSet);
        } catch (NoClassDefFoundError ncde) {
            String msg = ncde.getMessage();
            // 处理包含 StaticLoggerBinder 的异常：
            //      如果异常消息包含 org.slf4j.impl.StaticLoggerBinder，则设置初始化状态为 NOP_FALLBACK_INITIALIZATION。
            //      报告加载 StaticLoggerBinder 失败的信息。
            //      报告默认使用无操作 (NOP) 日志实现的信息。
            //      提供详细信息的链接。
            if (messageContainsOrgSlf4jImplStaticLoggerBinder(msg)) {
                INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION;
                Util.report("Failed to load class \"org.slf4j.impl.StaticLoggerBinder\".");
                Util.report("Defaulting to no-operation (NOP) logger implementation");
                Util.report("See " + NO_STATICLOGGERBINDER_URL + " for further details.");
            } else {
                failedBinding(ncde);
                throw ncde;
            }
        } catch (java.lang.NoSuchMethodError nsme) {
            String msg = nsme.getMessage();
            if (msg != null && msg.contains("org.slf4j.impl.StaticLoggerBinder.getSingleton()")) {
                INITIALIZATION_STATE = FAILED_INITIALIZATION;
                Util.report("slf4j-api 1.6.x (or later) is incompatible with this binding.");
                Util.report("Your binding is version 1.5.5 or earlier.");
                Util.report("Upgrade your binding to version 1.6.x.");
            }
            throw nsme;
        } catch (Exception e) {
            failedBinding(e);
            throw new IllegalStateException("Unexpected initialization failure", e);
        } finally {
            // 清理工作：无论成功还是失败，执行清理工作。
            postBindCleanUp();
        }
    }

    /**
     * postBindCleanUp 方法的功能如下：
     * 1、修复替代日志记录器：
     *      调用 fixSubstituteLoggers 方法，将替代日志记录器（SubstituteLogger）替换为实际的日志记录器（Logger）。
     * 2、重放事件：
     *      调用 replayEvents 方法，将初始化过程中记录的事件重新发送给实际的日志记录器。
     * 3、释放资源：
     *      调用 SUBST_FACTORY.clear 方法，清除 SubstituteLoggerFactory 中的所有资源。
     */
    private static void postBindCleanUp() {
		fixSubstituteLoggers();
		replayEvents();
		// release all resources in SUBST_FACTORY
		SUBST_FACTORY.clear();
	}

    /**
     * 在日志框架初始化完成后，将 SubstituteLogger 替换为实际的 Logger 对象。
     * 1、同步：
     *      使用 synchronized (SUBST_FACTORY) 确保线程安全。
     * 2、初始化：
     *      调用 SUBST_FACTORY.postInitialization() 方法，
     *      标记替代日志记录器已经完成初始化。
     * 3、遍历：
     *      遍历 SUBST_FACTORY 中的所有 SubstituteLogger 对象。
     * 4、替换：
     *      对于每个 SubstituteLogger，通过 getLogger(substLogger.getName())
     *      获取实际的 Logger 对象，并调用 substLogger.setDelegate(logger)
     *      将其设置为代理。
     */
    private static void fixSubstituteLoggers() {
        synchronized (SUBST_FACTORY) {
            SUBST_FACTORY.postInitialization();
            for (SubstituteLogger substLogger : SUBST_FACTORY.getLoggers()) {
                Logger logger = getLogger(substLogger.getName());
                substLogger.setDelegate(logger);
            }
        }

    }

    // 绑定失败
    static void failedBinding(Throwable t) {
        INITIALIZATION_STATE = FAILED_INITIALIZATION;
        Util.report("Failed to instantiate SLF4J LoggerFactory", t);
    }

    /**
     * 将初始化过程中记录的事件重新发送给实际的日志记录器。
     * 1、获取事件队列：
     *      从 SUBST_FACTORY 获取 LinkedBlockingQueue 类型的事件队列 queue。
     * 2、初始化变量：
     *      定义 queueSize 为队列的大小，count 用于计数，
     *      maxDrain 为每次从队列中取出的最大事件数，
     *      eventList 用于存储从队列中取出的事件。
     * 3、循环处理事件：
     *      使用 drainTo 方法从队列中取出最多 maxDrain 个事件到 eventList。
     *      如果没有事件被取出（numDrained == 0），则退出循环。
     *      遍历 eventList 中的每个事件，
     *      调用 replaySingleEvent 方法重放单个事件。
     *      如果是第一次处理事件（count == 1），
     *      调用 emitReplayOrSubstituionWarning 方法发出警告。
     *      清空 eventList 以便下一次循环使用。
     */
    private static void replayEvents() {
        final LinkedBlockingQueue<SubstituteLoggingEvent> queue = SUBST_FACTORY.getEventQueue();
        final int queueSize = queue.size();
        int count = 0;
        final int maxDrain = 128;
        List<SubstituteLoggingEvent> eventList = new ArrayList<SubstituteLoggingEvent>(maxDrain);
        while (true) {
            int numDrained = queue.drainTo(eventList, maxDrain);
            if (numDrained == 0)
                break;
            for (SubstituteLoggingEvent event : eventList) {
                replaySingleEvent(event);
                if (count++ == 0)
                    emitReplayOrSubstituionWarning(event, queueSize);
            }
            eventList.clear();
        }
    }

    /**
     * 根据传入的 SubstituteLoggingEvent 对象和队列大小来决定输出哪种类型的警告信息
     * 1、检查 SubstituteLogger 是否为事件感知型：
     *      如果是，则调用 emitReplayWarning 方法输出重放警告。
     * 2、检查 SubstituteLogger 是否为无操作型：
     *      如果是，则不执行任何操作。
     * 3、其他情况：
     *      调用 emitSubstitutionWarning 方法输出替代警告。
     * @param event
     * @param queueSize
     */
    private static void emitReplayOrSubstituionWarning(SubstituteLoggingEvent event, int queueSize) {
        if (event.getLogger().isDelegateEventAware()) {
            emitReplayWarning(queueSize);
        } else if (event.getLogger().isDelegateNOP()) {
            // nothing to do
        } else {
            emitSubstitutionWarning();
        }
    }

    /**
     * 重放单个日志事件。具体步骤如下：
     * 1、检查传入的 event 是否为 null，如果是则直接返回。
     * 2、获取 event 对应的 SubstituteLogger 实例和日志器名称。
     * 3、检查 SubstituteLogger 的代理日志器是否为 null，
     *  如果是则抛出 IllegalStateException。
     * 4、检查 SubstituteLogger 的代理日志器是否为 NOP（无操作）日志器，
     *  如果是则不做任何处理。
     * 5、如果 SubstituteLogger 的代理日志器支持事件记录，
     *  则调用 log 方法记录事件。
     * 6、否则，调用 Util.report 方法报告日志器名称。
     * @param event
     */
    private static void replaySingleEvent(SubstituteLoggingEvent event) {
        if (event == null)
            return;

        SubstituteLogger substLogger = event.getLogger();
        String loggerName = substLogger.getName();
        if (substLogger.isDelegateNull()) {
            throw new IllegalStateException("Delegate logger cannot be null at this state.");
        }

        if (substLogger.isDelegateNOP()) {
            // nothing to do
        } else if (substLogger.isDelegateEventAware()) {
            substLogger.log(event);
        } else {
            Util.report(loggerName);
        }
    }

    /**
     * 输出警告信息，提示用户在初始化阶段可能访问了替代日志记录器，
     * 这些日志记录器在初始化阶段的调用被忽略，但后续的调用将正常工作。
     */
    private static void emitSubstitutionWarning() {
        Util.report("The following set of substitute loggers may have been accessed");
        Util.report("during the initialization phase. Logging calls during this");
        Util.report("phase were not honored. However, subsequent logging calls to these");
        Util.report("loggers will work as normally expected.");
        Util.report("See also " + SUBSTITUTE_LOGGER_URL);
    }

    // 输出重放警告
    private static void emitReplayWarning(int eventCount) {
        Util.report("A number (" + eventCount + ") of logging calls during the initialization phase have been intercepted and are");
        Util.report("now being replayed. These are subject to the filtering rules of the underlying logging system.");
        Util.report("See also " + REPLAY_URL);
    }

    /**
     * 检查当前使用的 SLF4J 绑定版本是否与兼容版本列表中的版本兼容。具体步骤如下：
     * 1、获取请求的 API 版本：
     *      从 StaticLoggerBinder 类中获取请求的 API 版本。
     * 2、检查版本兼容性：
     *      遍历兼容版本列表，检查请求的版本是否以列表中的任何一个版本开头。
     * 3、报告不兼容：
     *      如果请求的版本不兼容，调用 Util.report 方法报告错误信息，并提供进一步的详细链接。
     * 4、处理异常：
     * NoSuchFieldError：
     *      忽略此异常，因为只有声明了 REQUESTED_API_VERSION 字段的实现才会发出兼容性警告。
     * 其他 Throwable 异常：
     *      报告意外问题。
     */
    private final static void versionSanityCheck() {
        try {
            String requested = StaticLoggerBinder.REQUESTED_API_VERSION;

            boolean match = false;
            for (String aAPI_COMPATIBILITY_LIST : API_COMPATIBILITY_LIST) {
                if (requested.startsWith(aAPI_COMPATIBILITY_LIST)) {
                    match = true;
                }
            }
            if (!match) {
                Util.report("The requested version " + requested + " by your slf4j binding is not compatible with "
                                + Arrays.asList(API_COMPATIBILITY_LIST).toString());
                Util.report("See " + VERSION_MISMATCH + " for further details.");
            }
        } catch (java.lang.NoSuchFieldError nsfe) {
            // given our large user base and SLF4J's commitment to backward
            // compatibility, we cannot cry here. Only for implementations
            // which willingly declare a REQUESTED_API_VERSION field do we
            // emit compatibility warnings.
        } catch (Throwable e) {
            // we should never reach here
            Util.report("Unexpected problem occured during version sanity check", e);
        }
    }

    // We need to use the name of the StaticLoggerBinder class, but we can't
    // reference
    // the class itself.
    private static String STATIC_LOGGER_BINDER_PATH = "org/slf4j/impl/StaticLoggerBinder.class";

    /**
     * 查找并返回所有可能的 StaticLoggerBinder 类的路径。
     * 1、初始化集合：
     *      创建一个 LinkedHashSet 来存储路径，确保插入顺序。
     * 2、获取类加载器：
     *      尝试获取当前类的类加载器。
     * 3、获取资源路径：
     *      如果类加载器为 null，则使用系统类加载器获取资源。
     *      否则，使用当前类的类加载器获取资源。
     * 4、遍历资源路径：将每个资源路径添加到集合中。
     * 5、异常处理：
     *      如果在获取资源过程中发生 IOException，调用 Util.report 方法报告错误。
     * 返回结果：返回包含所有路径的集合。
     * @return
     */
    static Set<URL> findPossibleStaticLoggerBinderPathSet() {
        // use Set instead of list in order to deal with bug #138
        // LinkedHashSet appropriate here because it preserves insertion order
        // during iteration
        Set<URL> staticLoggerBinderPathSet = new LinkedHashSet<URL>();
        try {
            ClassLoader loggerFactoryClassLoader = LoggerFactory.class.getClassLoader();
            Enumeration<URL> paths;
            if (loggerFactoryClassLoader == null) {
                paths = ClassLoader.getSystemResources(STATIC_LOGGER_BINDER_PATH);
            } else {
                paths = loggerFactoryClassLoader.getResources(STATIC_LOGGER_BINDER_PATH);
            }
            while (paths.hasMoreElements()) {
                URL path = paths.nextElement();
                staticLoggerBinderPathSet.add(path);
            }
        } catch (IOException ioe) {
            Util.report("Error getting resources from path", ioe);
        }
        return staticLoggerBinderPathSet;
    }

    // 方法检查 binderPathSet 是否包含多个URL。
    private static boolean isAmbiguousStaticLoggerBinderPathSet(Set<URL> binderPathSet) {
        return binderPathSet.size() > 1;
    }

    /**
     * Prints a warning message on the console if multiple bindings were found
     * on the class path. No reporting is done otherwise.
     * 
     */
    /**
     * 检查类路径中是否存在多个SLF4J绑定，并在存在多个绑定时打印警告信息。
     * 1、检查多个绑定：
     *      调用 isAmbiguousStaticLoggerBinderPathSet
     *      方法检查 binderPathSet 是否包含多个URL。
     * 2、打印警告信息：
     *      如果存在多个绑定，使用 Util.report 方法打印警告信息，
     *      包括发现的每个绑定的路径，并提供一个链接指向详细解释。
     * @param binderPathSet
     */
    private static void reportMultipleBindingAmbiguity(Set<URL> binderPathSet) {
        if (isAmbiguousStaticLoggerBinderPathSet(binderPathSet)) {
            Util.report("Class path contains multiple SLF4J bindings.");
            for (URL path : binderPathSet) {
                Util.report("Found binding in [" + path + "]");
            }
            Util.report("See " + MULTIPLE_BINDINGS_URL + " for an explanation.");
        }
    }

    // 是否是Android环境
    private static boolean isAndroid() {
        String vendor = Util.safeGetSystemProperty(JAVA_VENDOR_PROPERTY);
        if (vendor == null)
            return false;
        return vendor.toLowerCase().contains("android");
    }

    /**
     * 这段代码的功能是报告实际绑定的日志工厂类型
     * 1、检查 binderPathSet 是否为 null。
     * 2、如果 binderPathSet 不为 null 且存在多个绑定路径，
     *      则调用 Util.report 方法报告实际绑定的日志工厂类型。
     * @param binderPathSet
     */
    private static void reportActualBinding(Set<URL> binderPathSet) {
        // binderPathSet can be null under Android
        if (binderPathSet != null && isAmbiguousStaticLoggerBinderPathSet(binderPathSet)) {
            Util.report("Actual binding is of type [" + StaticLoggerBinder.getSingleton().getLoggerFactoryClassStr() + "]");
        }
    }

    /**
     * Return a logger named according to the name parameter using the
     * statically bound {@link ILoggerFactory} instance.
     * 
     * @param name
     *            The name of the logger.
     * @return logger
     */
    public static Logger getLogger(String name) {
        // 每种日志框架都实现了LoggerFactory 这里就是根据集成的日志框架获得对应的factory
        ILoggerFactory iLoggerFactory = getILoggerFactory();
        return iLoggerFactory.getLogger(name);
    }

    /**
     * Return a logger named corresponding to the class passed as parameter,
     * using the statically bound {@link ILoggerFactory} instance.
     * 
     * <p>
     * In case the the <code>clazz</code> parameter differs from the name of the
     * caller as computed internally by SLF4J, a logger name mismatch warning
     * will be printed but only if the
     * <code>slf4j.detectLoggerNameMismatch</code> system property is set to
     * true. By default, this property is not set and no warnings will be
     * printed even in case of a logger name mismatch.
     * 
     * @param clazz
     *            the returned logger will be named after clazz
     * @return logger
     * 
     * 
     * @see <a
     *      href="http://www.slf4j.org/codes.html#loggerNameMismatch">Detected
     *      logger name mismatch</a>
     */
    /**
     * 1、获取 Logger 实例：
     *      调用 getLogger(clazz.getName()) 方法，根据类名获取 Logger 实例。
     * 2、日志名称不匹配检测：
     *      检查 DETECT_LOGGER_NAME_MISMATCH 是否为 true，如果是则继续检测。
     *      调用 Util.getCallingClass() 获取调用该方法的类。
     *      比较传入的 clazz 和自动计算的调用类 autoComputedCallingClass，如果不匹配则报告错误。
     * 3、返回 Logger 实例：
     *      返回获取到的 Logger 实例。
     * @param clazz
     * @return Logger
     */
    public static Logger getLogger(Class<?> clazz) {
        Logger logger = getLogger(clazz.getName());
        if (DETECT_LOGGER_NAME_MISMATCH) {
            Class<?> autoComputedCallingClass = Util.getCallingClass();
            if (autoComputedCallingClass != null && nonMatchingClasses(clazz, autoComputedCallingClass)) {
                Util.report(String.format("Detected logger name mismatch. Given name: \"%s\"; computed name: \"%s\".", logger.getName(),
                                autoComputedCallingClass.getName()));
                Util.report("See " + LOGGER_NAME_MISMATCH_URL + " for an explanation");
            }
        }
        return logger;
    }

    // 比较传入的 clazz 和自动计算的调用类 autoComputedCallingClass
    private static boolean nonMatchingClasses(Class<?> clazz, Class<?> autoComputedCallingClass) {
        return !autoComputedCallingClass.isAssignableFrom(clazz);
    }

    /**
     * Return the {@link ILoggerFactory} instance in use.
     * <p/>
     * <p/>
     * ILoggerFactory instance is bound with this class at compile time.
     * 
     * @return the ILoggerFactory instance in use
     */
    /**
     * 获取ILoggerFactory实例
     *  1、初始化检查
     *      首先检查 INITIALIZATION_STATE 是否为 UNINITIALIZED。
     *      如果是，则进行同步处理，确保只有一个线程执行初始化操作。
     *  2、执行初始化
     *      在同步块内，再次检查 INITIALIZATION_STATE 是否仍为 UNINITIALIZED，
     *      如果是，则将其设置为 ONGOING_INITIALIZATION
     *      并调用 performInitialization() 方法进行实际的初始化操作。
     *  3、返回工厂实例
     *      根据 INITIALIZATION_STATE 的值，返回相应的 ILoggerFactory 实例
     *          SUCCESSFUL_INITIALIZATION：返回 StaticLoggerBinder 单例的 ILoggerFactory。
     *          NOP_FALLBACK_INITIALIZATION：返回 NOPLoggerFactory。
     *          FAILED_INITIALIZATION：抛出 IllegalStateException。
     *          ONGOING_INITIALIZATION：支持重入行为，返回 SubstituteLoggerFactory。
     * @return ILoggerFactory
     */
    public static ILoggerFactory getILoggerFactory() {
        if (INITIALIZATION_STATE == UNINITIALIZED) {
            synchronized (LoggerFactory.class) {
                if (INITIALIZATION_STATE == UNINITIALIZED) {
                    INITIALIZATION_STATE = ONGOING_INITIALIZATION;
                    performInitialization();
                }
            }
        }
        switch (INITIALIZATION_STATE) {
        case SUCCESSFUL_INITIALIZATION:
            return StaticLoggerBinder.getSingleton().getLoggerFactory();
        case NOP_FALLBACK_INITIALIZATION:
            return NOP_FALLBACK_FACTORY;
        case FAILED_INITIALIZATION:
            throw new IllegalStateException(UNSUCCESSFUL_INIT_MSG);
        case ONGOING_INITIALIZATION:
            // support re-entrant behavior.
            // See also http://jira.qos.ch/browse/SLF4J-97
            return SUBST_FACTORY;
        }
        throw new IllegalStateException("Unreachable code");
    }
}
