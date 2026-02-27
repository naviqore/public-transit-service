package org.naviqore.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;

@Controller
@ConditionalOnProperty("app.home-page.enabled")
public class HomeController {

    private final String version;
    private final String description;
    private final String buildTime;

    @Autowired
    public HomeController(BuildProperties buildProperties) {
        this.version = buildProperties.getVersion();
        String desc = buildProperties.get("description");
        this.description = (desc != null && desc.endsWith(".")) ? desc.substring(0, desc.length() - 1) : desc;
        this.buildTime = buildProperties.getTime() != null ? DateTimeFormatter.ISO_INSTANT.format(
                buildProperties.getTime()) : "Unknown";
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("productName", "Naviqore");
        model.addAttribute("serviceName", "Public Transit Service");
        model.addAttribute("appVersion", version);
        model.addAttribute("appDescription", description);
        model.addAttribute("buildTime", buildTime);
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name"));
        model.addAttribute("osArch", System.getProperty("os.arch"));

        return "home";
    }
}