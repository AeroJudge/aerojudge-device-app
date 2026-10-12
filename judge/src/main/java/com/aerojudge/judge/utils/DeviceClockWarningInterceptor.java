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
    private final DeviceClockService deviceClockService;

    public DeviceClockWarningInterceptor(DeviceClockService deviceClockService) {
        this.deviceClockService = deviceClockService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("GET".equals(request.getMethod()) && handler instanceof HandlerMethod method
                && (RootController.class.isAssignableFrom(method.getBeanType())
                    || ValidationController.class.isAssignableFrom(method.getBeanType()))
                && !(request.getContextPath() + "/device-time-warning").equals(request.getRequestURI())
                && deviceClockService.isTimeLossWarningPending()) {
            // Do not consume the flag: kiosk probes and browser reloads must both see it.
            response.sendRedirect(request.getContextPath() + "/device-time-warning");
            return false;
        }
        return true;
    }
}
