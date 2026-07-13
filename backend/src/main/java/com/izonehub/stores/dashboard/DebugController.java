package com.izonehub.stores.dashboard;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DebugController {
    @RequestMapping("/api/debug-headers")
    public Map<String, String> debug(HttpServletRequest req) {
        Map<String, String> map = new HashMap<>();
        Enumeration<String> names = req.getHeaderNames();
        while(names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, req.getHeader(name));
        }
        return map;
    }
}
