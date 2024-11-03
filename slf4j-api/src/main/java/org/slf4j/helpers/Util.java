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
package org.slf4j.helpers;

/**
 * An internal utility class.
 *
 * @author Alexander Dorokhine
 * @author Ceki G&uuml;lc&uuml;
 */
public final class Util {

	
    private Util() {
    }

    public static String safeGetSystemProperty(String key) {
        if (key == null)
            throw new IllegalArgumentException("null input");

        String result = null;
        try {
            result = System.getProperty(key);
        } catch (java.lang.SecurityException sm) {
            ; // ignore
        }
        return result;
    }

    public static boolean safeGetBooleanSystemProperty(String key) {
        String value = safeGetSystemProperty(key);
        if (value == null)
            return false;
        else
            return value.equalsIgnoreCase("true");
    }

    /**
     * In order to call {@link SecurityManager#getClassContext()}, which is a
     * protected method, we add this wrapper which allows the method to be visible
     * inside this package.
     */
    private static final class ClassContextSecurityManager extends SecurityManager {
        // 返回当前线程的类上下文数组，这对于日志记录、调试和其他需要了解调用栈信息的场景非常有用。
        protected Class<?>[] getClassContext() {
            return super.getClassContext();
        }
    }

    private static ClassContextSecurityManager SECURITY_MANAGER;
    // 是否已经尝试创建SecurityManager
    private static boolean SECURITY_MANAGER_CREATION_ALREADY_ATTEMPTED = false;

    /**
     * 获取ClassContextSecurityManager实例
     * 1、检查已存在的实例：
     *      如果 SECURITY_MANAGER 已经被初始化，则直接返回该实例。
     * 2、检查是否已经尝试过创建：
     *      如果已经尝试过创建但失败了，则返回 null。
     * 3、创建新实例：
     *      如果上述两个条件都不满足，则调用 safeCreateSecurityManager
     *      方法尝试创建一个新的 ClassContextSecurityManager 实例，并将其赋值给 SECURITY_MANAGER，同时将 SECURITY_MANAGER_CREATION_ALREADY_ATTEMPTED 标记为 true，最后返回新创建的实例。
     * @return
     */
    private static ClassContextSecurityManager getSecurityManager() {
        if (SECURITY_MANAGER != null)
            return SECURITY_MANAGER;
        else if (SECURITY_MANAGER_CREATION_ALREADY_ATTEMPTED)
            return null;
        else {
            SECURITY_MANAGER = safeCreateSecurityManager();
            SECURITY_MANAGER_CREATION_ALREADY_ATTEMPTED = true;
            return SECURITY_MANAGER;
        }
    }

    // 用于安全创建SecurityManager实例
    private static ClassContextSecurityManager safeCreateSecurityManager() {
        try {
            return new ClassContextSecurityManager();
        } catch (java.lang.SecurityException sm) {
            return null;
        }
    }

    /**
     * Returns the name of the class which called the invoking method.
     *
     * @return the name of the class which called the invoking method.
     */
    /**
     * 返回调用调用方法的类（Class）
     * 1、获取 SecurityManager 实例：
     *      调用 getSecurityManager 方法获取 ClassContextSecurityManager 实例。
     * 2、检查 SecurityManager 实例是否存在：
     *      如果 SecurityManager 实例为 null，直接返回 null。
     * 3、获取调用栈：
     *      调用 securityManager.getClassContext() 获取当前线程的调用栈。
     * 4、查找 Util 类的位置：
     *      遍历调用栈，找到 Util 类的位置。
     * 5、验证调用栈的有效性：
     *      检查 Util 类及其调用者在调用栈中是否存在，
     *      如果不存在则抛出 IllegalStateException 异常。
     * 6、返回调用者的调用者：
     *      返回调用 Util 类的方法的调用者的类。
     * @return
     */
    // 返回调用调用方法的类（Class）
    public static Class<?> getCallingClass() {
        ClassContextSecurityManager securityManager = getSecurityManager();
        if (securityManager == null)
            return null;
        Class<?>[] trace = securityManager.getClassContext();
        String thisClassName = Util.class.getName();

        // Advance until Util is found
        int i;
        for (i = 0; i < trace.length; i++) {
            if (thisClassName.equals(trace[i].getName()))
                break;
        }

        // trace[i] = Util; trace[i+1] = caller; trace[i+2] = caller's caller
        if (i >= trace.length || i + 2 >= trace.length) {
            throw new IllegalStateException("Failed to find org.slf4j.helpers.Util or its caller in the stack; " + "this should not happen");
        }

        return trace[i + 2];
    }

    static final public void report(String msg, Throwable t) {
        System.err.println(msg);
        System.err.println("Reported exception:");
        t.printStackTrace();
    }

    static final public void report(String msg) {
        System.err.println("SLF4J: " + msg);
    }
    
	

}
