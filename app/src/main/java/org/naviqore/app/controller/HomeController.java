package org.naviqore.app.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnBooleanProperty(name = "naviqore.home-page.enabled", matchIfMissing = true)
public class HomeController {

    private final @Nullable String version;
    private final @Nullable String description;

    @Autowired
    public HomeController(BuildProperties buildProperties) {
        this.version = buildProperties.getVersion();
        String desc = buildProperties.get("description");
        this.description = (desc != null && desc.endsWith(".")) ? desc.substring(0, desc.length() - 1) : desc;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("productName", "Naviqore");
        model.addAttribute("serviceName", "Public Transit Service");
        model.addAttribute("appVersion", version);
        model.addAttribute("appDescription", description);
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name"));
        model.addAttribute("osArch", System.getProperty("os.arch"));

        return "home";
    }
}