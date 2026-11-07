package com.aerojudge.judge.utils;

import com.aerojudge.judge.controller.RootController;
import com.aerojudge.judge.controller.ValidationController;
import com.aerojudge.judge.service.DeviceClockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DeviceClockWarningInterceptor implements HandlerInterceptor {
    private final DeviceClockService clock;
    public DeviceClockWarningInterceptor(DeviceClockService clock) { this.clock = clock; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("GET".equals(request.getMethod()) && clock.isBootWarningPending()
                && !"/device-time-warning".equals(request.getServletPath())
                && !"/device-time-warning".equals(request.getRequestURI())
                && handler instanceof HandlerMethod method
                && (RootController.class.isAssignableFrom(method.getBeanType())
                    || ValidationController.class.isAssignableFrom(method.getBeanType()))) {
            // A kiosk readiness probe is also a GET: redirecting never consumes the warning.
            response.sendRedirect("/device-time-warning");
            return false;
        }
        return true;
    }
}
