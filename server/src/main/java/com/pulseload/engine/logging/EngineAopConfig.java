package com.pulseload.engine.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Boot's own {@code AopAutoConfiguration} already enables CGLIB proxying once AspectJ is on the
 * classpath, but it doesn't turn on {@code exposeProxy}. This escalates that one setting on the
 * same shared proxy-creator bean — {@code EngineRun} needs {@code AopContext.currentProxy()} to
 * route its scheduled snapshot tick back through the proxy (see its javadoc for why).
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
public class EngineAopConfig {
}
